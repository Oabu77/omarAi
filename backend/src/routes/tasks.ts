import type { Context, Hono } from "hono";
import { CloudflareWorkersAiProvider } from "../ai/cloudflare";
import { AiProviderError } from "../ai/provider";
import { enforceApprovalPolicy } from "../approval-policy";
import { isModerationReportState, isRecord, optionalString, requireString } from "../contracts";
import { currentUserId, database, d1Rows, writeAudit } from "../db";
import { verifyActionEvidence } from "../evidence";
import { ApiError, readJsonObject, success } from "../http";
import { withIdempotency } from "../idempotency";
import { assistantRateLimit, enforceRateLimit, internalTokenMatches } from "../security";
import type { AppEnvironment, TaskReceipt, TaskStatus, VerificationState } from "../types";

interface TaskRow {
  id: string;
  user_id: string;
  business_id: string | null;
  conversation_id: string | null;
  title: string;
  intent: string;
  status: TaskStatus;
  verification_state: VerificationState;
  cancellable: number;
  error_code: string | null;
  error_message: string | null;
  created_at: string;
  updated_at: string;
  started_at: string | null;
  completed_at: string | null;
  evidence_provider?: string | null;
  evidence_reference?: string | null;
}

interface StepRow {
  id: string;
  ordinal: number;
  agent: string;
  action: string;
  status: string;
  requires_approval: number;
  external_action: number;
  result_summary: string | null;
}

function receipt(row: TaskRow): TaskReceipt {
  return {
    taskId: row.id,
    status: row.status,
    verificationState: row.verification_state,
    approvalRequired: row.status === "waiting_approval",
    cancellable: row.cancellable === 1 && !["completed", "failed", "cancelled"].includes(row.status),
    createdAt: row.created_at,
    providerEvidence: {
      state: row.verification_state === "provider_verified" ? "PROVIDER_VERIFIED" : "NONE",
      provider: row.evidence_provider ?? null,
      referenceId: row.evidence_reference ?? null,
    },
  };
}

async function ownedTask(db: D1Database, userId: string, taskId: string): Promise<TaskRow> {
  const row = await db
    .prepare(
      `SELECT id, user_id, business_id, conversation_id, title, intent, status, verification_state,
              cancellable, error_code, error_message, created_at, updated_at, started_at, completed_at,
              (SELECT provider FROM task_reports r WHERE r.task_id = tasks.id AND r.evidence_state = 'provider_verified'
                ORDER BY r.created_at DESC LIMIT 1) AS evidence_provider,
              (SELECT provider_reference FROM task_reports r WHERE r.task_id = tasks.id AND r.evidence_state = 'provider_verified'
                ORDER BY r.created_at DESC LIMIT 1) AS evidence_reference
       FROM tasks WHERE id = ? AND user_id = ?`,
    )
    .bind(taskId, userId)
    .first<TaskRow>();
  if (!row) throw new ApiError(404, "TASK_NOT_FOUND", "Task not found.");
  return row;
}

function planInput(body: Record<string, unknown>): {
  text: string;
  locale: string;
  conversationId: string;
  businessId?: string;
} {
  const nested = isRecord(body.input) ? body.input : undefined;
  const rawText = body.text ?? body.message ?? nested?.text;
  const text = requireString(rawText, "text", 12_000);
  const locale = optionalString(body.locale, "locale", 35) ?? "en";
  const conversationId = optionalString(body.conversationId, "conversationId", 128) ?? crypto.randomUUID();
  const businessId = optionalString(body.businessId, "businessId", 128);
  return { text, locale, conversationId, ...(businessId ? { businessId } : {}) };
}

export function registerTaskRoutes(app: Hono<AppEnvironment>): void {
  const createPlan = async (c: Context<AppEnvironment>): Promise<Response> => {
    const userId = currentUserId(c);
    return withIdempotency(c, userId, async () => {
      await enforceRateLimit(c.env, userId, "assistant-plan", assistantRateLimit(c.env));
      const body = await readJsonObject(c);
      const input = planInput(body);
      if (body.attachments !== undefined || body.fileIds !== undefined) {
        throw new ApiError(
          503,
          "FILE_SERVICE_DISCONNECTED",
          "File and media planning requires a configured file service; none is connected in this backend.",
        );
      }
      const db = database(c);
      if (input.businessId) {
        const access = await db
          .prepare("SELECT 1 AS allowed FROM business_memberships WHERE business_id = ? AND user_id = ?")
          .bind(input.businessId, userId)
          .first<{ allowed: number }>();
        if (!access) throw new ApiError(404, "BUSINESS_NOT_FOUND", "Business not found or access is not permitted.");
      }

      const priorMessages = await db
        .prepare(
          `SELECT role, substr(content, 1, 2000) AS content
             FROM assistant_messages
            WHERE user_id = ? AND conversation_id = ? AND role IN ('user', 'assistant')
            ORDER BY created_at DESC LIMIT 12`,
        )
        .bind(userId, input.conversationId)
        .all<{ role: "user" | "assistant"; content: string }>();
      const history = priorMessages.results
        .filter((message) => (message.role === "user" || message.role === "assistant") && typeof message.content === "string")
        .reverse();

      const taskId = crypto.randomUUID();
      const userMessageId = crypto.randomUUID();
      const now = new Date().toISOString();
      await db.batch([
        db
          .prepare(
            `INSERT INTO tasks
             (id, user_id, business_id, conversation_id, title, intent, status, verification_state, created_at, updated_at, started_at)
             VALUES (?, ?, ?, ?, ?, ?, 'planning', 'not_executed', ?, ?, ?)`,
          )
          .bind(taskId, userId, input.businessId ?? null, input.conversationId, "Planning request", "unclassified", now, now, now),
        db
          .prepare(
            `INSERT INTO assistant_messages (id, conversation_id, user_id, role, content, created_at)
             VALUES (?, ?, ?, 'user', ?, ?)`,
          )
          .bind(userMessageId, input.conversationId, userId, input.text, now),
      ]);

      const provider = new CloudflareWorkersAiProvider(c.env);
      let planned;
      try {
        planned = await provider.plan({ ...input, history });
      } catch (error) {
        const providerError = error instanceof AiProviderError
          ? error
          : new AiProviderError("AI_PROVIDER_FAILED", "The AI planning provider failed.");
        const failedAt = new Date().toISOString();
        await db
          .prepare(
            `UPDATE tasks SET status = 'failed', verification_state = 'failed', error_code = ?, error_message = ?,
                              updated_at = ?, completed_at = ? WHERE id = ?`,
          )
          .bind(providerError.code, providerError.message, failedAt, failedAt, taskId)
          .run();
        await writeAudit(db, {
          actorUserId: userId,
          actorType: "user",
          action: "task.plan",
          targetType: "task",
          targetId: taskId,
          outcome: "failure",
          requestId: c.get("requestId"),
          metadata: { errorCode: providerError.code },
        });
        const failedReceipt: TaskReceipt = {
          taskId,
          status: "failed",
          verificationState: "failed",
          approvalRequired: false,
          cancellable: false,
          createdAt: now,
          providerEvidence: { state: "NONE", provider: null, referenceId: null },
        };
        throw new ApiError(
          providerError.code === "AI_DISCONNECTED" ? 503 : 502,
          providerError.code,
          providerError.message,
          { taskReceipt: failedReceipt },
        );
      }

      const enforced = enforceApprovalPolicy(input.text, planned.plan);
      const approvalRequired = enforced.approvalRequired;
      const status: TaskStatus = approvalRequired ? "waiting_approval" : "planned";
      const assistantMessageId = crypto.randomUUID();
      const plannedAt = new Date().toISOString();
      const statements = enforced.plan.steps.map((step, ordinal) =>
        db
          .prepare(
            `INSERT INTO task_steps
             (id, task_id, ordinal, agent, action, status, requires_approval, external_action, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          )
          .bind(
            crypto.randomUUID(),
            taskId,
            ordinal,
            step.agent,
            step.action,
            step.requiresApproval ? "waiting_approval" : "planned",
            step.requiresApproval ? 1 : 0,
            step.externalAction ? 1 : 0,
            plannedAt,
            plannedAt,
          ),
      );
      await db.batch([
        db
          .prepare(
            `UPDATE tasks SET title = ?, intent = ?, status = ?, updated_at = ? WHERE id = ?`,
          )
          .bind(planned.plan.title, planned.plan.intent, status, plannedAt, taskId),
        ...statements,
        db
          .prepare(
            `INSERT INTO assistant_messages
             (id, conversation_id, user_id, role, content, model_provider, model_id, created_at)
             VALUES (?, ?, ?, 'assistant', ?, ?, ?, ?)`,
          )
          .bind(
            assistantMessageId,
            input.conversationId,
            userId,
            planned.plan.reply,
            planned.provider,
            planned.model,
            plannedAt,
          ),
        db
          .prepare(
            `INSERT INTO task_reports
             (id, task_id, reporter_type, provider, model_id, output_text, reported_state, evidence_state, created_at)
             VALUES (?, ?, 'ai_provider', ?, ?, ?, 'prepared', 'provider_verified', ?)`,
          )
          .bind(crypto.randomUUID(), taskId, planned.provider, planned.model, planned.plan.reply, plannedAt),
      ]);
      await writeAudit(db, {
        actorUserId: userId,
        actorType: "user",
        action: "task.plan",
        targetType: "task",
        targetId: taskId,
        outcome: "success",
        requestId: c.get("requestId"),
        metadata: {
          status,
          approvalRequired,
          approvalPolicy: "server-enforced-v1",
          approvalPolicyReasons: enforced.policyReasons,
          modelProvider: planned.provider,
        },
      });

      const taskReceipt: TaskReceipt = {
        taskId,
        status,
        verificationState: "not_executed",
        approvalRequired,
        cancellable: true,
        createdAt: now,
        providerEvidence: {
          state: "PROVIDER_VERIFIED",
          provider: planned.provider,
          referenceId: null,
        },
      };
      return success(c, {
        conversationId: input.conversationId,
        messageId: assistantMessageId,
        reply: planned.plan.reply,
        plan: {
          title: enforced.plan.title,
          intent: enforced.plan.intent,
          steps: enforced.plan.steps.map((step, index) => ({ ordinal: index, ...step })),
        },
        taskReceipt,
        provider: {
          name: planned.provider,
          model: planned.model,
          inferenceVerified: true,
          externalActionsExecuted: false,
        },
      }, 201);
    });
  };

  app.post("/v1/tasks/plan", createPlan);
  app.post("/v1/assistant/messages", createPlan);

  app.get("/v1/tasks", async (c) => {
    const userId = currentUserId(c);
    const status = c.req.query("status");
    const allowedStatuses = new Set<TaskStatus>([
      "planning", "planned", "waiting_approval", "queued", "running", "completed", "failed", "cancelled",
    ]);
    if (status && !allowedStatuses.has(status as TaskStatus)) {
      throw new ApiError(400, "INVALID_STATUS", "The requested task status is invalid.");
    }
    const db = database(c);
    const result = status
      ? await db
          .prepare(
            `SELECT id, user_id, business_id, conversation_id, title, intent, status, verification_state,
                    cancellable, error_code, error_message, created_at, updated_at, started_at, completed_at,
                    (SELECT provider FROM task_reports r WHERE r.task_id = tasks.id AND r.evidence_state = 'provider_verified'
                      ORDER BY r.created_at DESC LIMIT 1) AS evidence_provider,
                    (SELECT provider_reference FROM task_reports r WHERE r.task_id = tasks.id AND r.evidence_state = 'provider_verified'
                      ORDER BY r.created_at DESC LIMIT 1) AS evidence_reference
             FROM tasks WHERE user_id = ? AND status = ? ORDER BY created_at DESC LIMIT 100`,
          )
          .bind(userId, status)
          .all<TaskRow>()
      : await db
          .prepare(
            `SELECT id, user_id, business_id, conversation_id, title, intent, status, verification_state,
                    cancellable, error_code, error_message, created_at, updated_at, started_at, completed_at,
                    (SELECT provider FROM task_reports r WHERE r.task_id = tasks.id AND r.evidence_state = 'provider_verified'
                      ORDER BY r.created_at DESC LIMIT 1) AS evidence_provider,
                    (SELECT provider_reference FROM task_reports r WHERE r.task_id = tasks.id AND r.evidence_state = 'provider_verified'
                      ORDER BY r.created_at DESC LIMIT 1) AS evidence_reference
             FROM tasks WHERE user_id = ? ORDER BY created_at DESC LIMIT 100`,
          )
          .bind(userId)
          .all<TaskRow>();
    return success(c, { tasks: d1Rows(result).map((row) => ({ ...row, receipt: receipt(row) })) });
  });

  app.get("/v1/tasks/:id", async (c) => {
    const db = database(c);
    const task = await ownedTask(db, currentUserId(c), c.req.param("id"));
    const steps = await db
      .prepare(
        `SELECT id, ordinal, agent, action, status, requires_approval, external_action, result_summary
         FROM task_steps WHERE task_id = ? ORDER BY ordinal`,
      )
      .bind(task.id)
      .all<StepRow>();
    return success(c, {
      task: { ...task, receipt: receipt(task) },
      steps: d1Rows(steps).map((step) => ({
        id: step.id,
        ordinal: step.ordinal,
        agent: step.agent,
        action: step.action,
        status: step.status,
        requiresApproval: step.requires_approval === 1,
        externalAction: step.external_action === 1,
        resultSummary: step.result_summary,
      })),
    });
  });

  app.post("/v1/tasks/:id/approve", async (c) => {
    const userId = currentUserId(c);
    return withIdempotency(c, userId, async () => {
      const body = await readJsonObject(c);
      if (body.approved !== true) {
        throw new ApiError(400, "EXPLICIT_APPROVAL_REQUIRED", "Set approved to true to approve this task.");
      }
      const db = database(c);
      const task = await ownedTask(db, userId, c.req.param("id"));
      if (task.status !== "waiting_approval") {
        throw new ApiError(409, "TASK_NOT_WAITING_FOR_APPROVAL", `Task status is ${task.status}.`);
      }
      const pending = await db
        .prepare("SELECT id FROM task_steps WHERE task_id = ? AND status = 'waiting_approval' ORDER BY ordinal")
        .bind(task.id)
        .all<{ id: string }>();
      if (pending.results.length === 0) {
        throw new ApiError(409, "NO_APPROVAL_PENDING", "This task has no steps waiting for approval.");
      }
      const now = new Date().toISOString();
      const scope = { stepIds: pending.results.map((row) => row.id) };
      await db.batch([
        db
          .prepare(
            `INSERT INTO approvals (id, task_id, user_id, decision, scope_json, decided_at)
             VALUES (?, ?, ?, 'approved', ?, ?)`,
          )
          .bind(crypto.randomUUID(), task.id, userId, JSON.stringify(scope), now),
        db
          .prepare("UPDATE task_steps SET status = 'queued', updated_at = ? WHERE task_id = ? AND status = 'waiting_approval'")
          .bind(now, task.id),
        db.prepare("UPDATE tasks SET status = 'queued', updated_at = ? WHERE id = ?").bind(now, task.id),
      ]);
      await writeAudit(db, {
        actorUserId: userId,
        actorType: "user",
        action: "task.approve",
        targetType: "task",
        targetId: task.id,
        outcome: "success",
        requestId: c.get("requestId"),
        metadata: scope,
      });
      return success(c, {
        taskReceipt: {
          ...receipt({ ...task, status: "queued", updated_at: now }),
          approvalRequired: false,
        },
        execution: {
          state: "QUEUED",
          note: "Approval was recorded. This response does not claim any external action completed.",
        },
      });
    });
  });

  app.post("/v1/tasks/:id/cancel", async (c) => {
    const userId = currentUserId(c);
    return withIdempotency(c, userId, async () => {
      await readJsonObject(c);
      const db = database(c);
      const task = await ownedTask(db, userId, c.req.param("id"));
      if (["completed", "failed", "cancelled"].includes(task.status)) {
        throw new ApiError(409, "TASK_NOT_CANCELLABLE", `Task status is ${task.status}.`);
      }
      if (task.cancellable !== 1) throw new ApiError(409, "TASK_NOT_CANCELLABLE", "This task cannot be cancelled.");
      const now = new Date().toISOString();
      await db.batch([
        db
          .prepare(
            `UPDATE tasks SET status = 'cancelled', verification_state = 'not_executed', updated_at = ?, completed_at = ?
             WHERE id = ?`,
          )
          .bind(now, now, task.id),
        db
          .prepare(
            `UPDATE task_steps SET status = 'cancelled', updated_at = ?
             WHERE task_id = ? AND status NOT IN ('completed', 'failed', 'cancelled')`,
          )
          .bind(now, task.id),
      ]);
      await writeAudit(db, {
        actorUserId: userId,
        actorType: "user",
        action: "task.cancel",
        targetType: "task",
        targetId: task.id,
        outcome: "success",
        requestId: c.get("requestId"),
      });
      return success(c, {
        taskReceipt: receipt({
          ...task,
          status: "cancelled",
          verification_state: "not_executed",
          cancellable: 0,
          updated_at: now,
          completed_at: now,
        }),
      });
    });
  });

  app.post("/v1/reports/ai-output", async (c) => {
    const userId = currentUserId(c);
    return withIdempotency(c, userId, async () => {
      const body = await readJsonObject(c);
      const taskId = requireString(body.taskId, "taskId", 128);
      const provider = requireString(body.provider, "provider", 120);
      const modelId = optionalString(body.modelId, "modelId", 200);
      const output = requireString(body.output, "output", 50_000);
      const reportedState = requireString(body.reportedState, "reportedState", 20);
      if (!isModerationReportState(reportedState)) {
        throw new ApiError(
          400,
          "AI_OUTPUT_REPORT_NOT_EXECUTION_RECEIPT",
          "AI output reports are moderation records and cannot submit, complete, or fail a task.",
        );
      }
      const db = database(c);
      const task = await ownedTask(db, userId, taskId);
      const category = optionalString(body.category, "category", 80);
      const reportId = crypto.randomUUID();
      const now = new Date().toISOString();
      await db
        .prepare(
          `INSERT INTO ai_output_reports
           (id, user_id, task_id, provider, model_id, output_text, category, status, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, 'received', ?, ?)`,
        )
        .bind(reportId, userId, task.id, provider, modelId, output, category, now, now)
        .run();
      await writeAudit(db, {
        actorUserId: userId,
        actorType: "user",
        action: "moderation.report_ai_output",
        targetType: "ai_output_report",
        targetId: reportId,
        outcome: "success",
        requestId: c.get("requestId"),
        metadata: { category, provider, taskTransitionApplied: false },
      });
      return success(c, {
        reportId,
        acceptedState: "prepared",
        evidence: { state: "CLIENT_SUPPLIED", provider, referenceId: null },
        taskTransitionApplied: false,
      }, 201);
    });
  });

  app.post("/v1/tasks/:id/execution-reports", async (c) => {
    const userId = currentUserId(c);
    return withIdempotency(c, userId, async () => {
      const internal = await internalTokenMatches(c.env, c.req.header("x-omar-internal-token"));
      if (!internal) {
        throw new ApiError(403, "EXECUTOR_AUTH_REQUIRED", "A trusted executor credential is required.");
      }
      const body = await readJsonObject(c);
      const provider = requireString(body.provider, "provider", 120);
      const modelId = optionalString(body.modelId, "modelId", 200);
      const output = requireString(body.output, "output", 50_000);
      const reportedState = requireString(body.reportedState, "reportedState", 20);
      if (!["submitted", "completed", "failed"].includes(reportedState)) {
        throw new ApiError(400, "INVALID_REPORTED_STATE", "Execution reportedState is invalid.");
      }
      const db = database(c);
      const task = await ownedTask(db, userId, c.req.param("id"));
      const evidence = isRecord(body.providerEvidence) ? body.providerEvidence : undefined;
      const providerReference = optionalString(evidence?.referenceId, "providerEvidence.referenceId", 500);
      const terminalClaim = reportedState === "submitted" || reportedState === "completed";
      let verifiedEvidence = null;
      let evidenceRejected = false;
      if (terminalClaim && providerReference) {
        try {
          verifiedEvidence = await verifyActionEvidence(c.env, {
            taskId: task.id,
            provider,
            referenceId: providerReference,
            reportedState: reportedState as "submitted" | "completed",
            requestId: c.get("requestId"),
          });
        } catch (error) {
          if (error instanceof ApiError && ["ACTION_EVIDENCE_REJECTED", "ACTION_EVIDENCE_MISMATCH"].includes(error.code)) {
            evidenceRejected = true;
          } else {
            throw error;
          }
        }
      }
      const evidenceState = verifiedEvidence
        ? "provider_verified"
        : evidenceRejected
          ? "rejected"
          : "client_supplied";
      const reportId = crypto.randomUUID();
      const now = new Date().toISOString();
      const statements = [
        db
          .prepare(
            `INSERT INTO task_reports
             (id, task_id, reporter_type, provider, model_id, output_text, reported_state, evidence_state,
              provider_reference, evidence_json, created_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          )
          .bind(
            reportId,
            task.id,
            "executor",
            provider,
            modelId,
            output,
            reportedState,
            evidenceState,
            providerReference,
            verifiedEvidence ? JSON.stringify(verifiedEvidence) : evidence ? JSON.stringify({ referenceId: providerReference }) : null,
            now,
          ),
      ];
      if (verifiedEvidence && reportedState === "completed") {
        statements.push(
          db
            .prepare(
              `UPDATE tasks SET status = 'completed', verification_state = 'provider_verified', updated_at = ?, completed_at = ?
               WHERE id = ?`,
            )
            .bind(now, now, task.id),
        );
      } else if (verifiedEvidence && reportedState === "submitted") {
        statements.push(
          db
            .prepare("UPDATE tasks SET status = 'running', verification_state = 'provider_verified', updated_at = ? WHERE id = ?")
            .bind(now, task.id),
        );
      } else if (reportedState === "failed") {
        statements.push(
          db
            .prepare(
              `UPDATE tasks SET status = 'failed', verification_state = 'failed', updated_at = ?, completed_at = ? WHERE id = ?`,
            )
            .bind(now, now, task.id),
        );
      }
      await db.batch(statements);
      await writeAudit(db, {
        actorUserId: userId,
        actorType: "service",
        action: "task.report_execution_result",
        targetType: "task_report",
        targetId: reportId,
        outcome: "success",
        requestId: c.get("requestId"),
        metadata: {
          reportedState,
          evidenceState,
          evidenceSource: verifiedEvidence ? "server-adapter" : "unverified-report",
          provider,
        },
      });
      const taskTransitionApplied = Boolean(
        verifiedEvidence && (reportedState === "submitted" || reportedState === "completed") ||
        reportedState === "failed",
      );
      return success(c, {
        reportId,
        acceptedState: reportedState,
        evidence: {
          state: evidenceState === "provider_verified"
            ? "PROVIDER_VERIFIED"
            : evidenceState === "rejected"
              ? "REJECTED"
              : "CLIENT_SUPPLIED",
          provider,
          referenceId: providerReference,
        },
        taskTransitionApplied,
      }, terminalClaim && !verifiedEvidence ? 202 : 201);
    });
  });

  app.get("/v1/reports/ai-output", async (c) => {
    const rows = await database(c)
      .prepare(
        `SELECT id, task_id, provider, model_id, category, status, created_at, updated_at
           FROM ai_output_reports WHERE user_id = ? ORDER BY created_at DESC LIMIT 200`,
      )
      .bind(currentUserId(c))
      .all();
    return success(c, { reports: rows.results });
  });

  app.get("/v1/tasks/:id/reports", async (c) => {
    const db = database(c);
    const task = await ownedTask(db, currentUserId(c), c.req.param("id"));
    const reports = await db
      .prepare(
        `SELECT id, reporter_type, provider, model_id, output_text, reported_state, evidence_state,
                provider_reference, evidence_json, created_at
         FROM task_reports WHERE task_id = ? ORDER BY created_at`,
      )
      .bind(task.id)
      .all();
    return success(c, { reports: reports.results });
  });

  app.get("/v1/audit", async (c) => {
    const rows = await database(c)
      .prepare(
        `SELECT id, actor_type, action, target_type, target_id, outcome, request_id, metadata_json, created_at
         FROM audit_logs WHERE actor_user_id = ? ORDER BY created_at DESC LIMIT 200`,
      )
      .bind(currentUserId(c))
      .all();
    return success(c, { events: rows.results });
  });
}
