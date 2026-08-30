import type { Context, Hono } from "hono";
import {
  calculateDocumentTotals,
  optionalString,
  requireNonNegativeInteger,
  requireString,
} from "../contracts";
import { currentUserId, database, d1Rows, requireBusinessRole, writeAudit } from "../db";
import { ApiError, readJsonObject, success } from "../http";
import { withIdempotency } from "../idempotency";
import type { AppEnvironment } from "../types";

const READ_ROLES = ["owner", "admin", "member", "viewer"] as const;
const WRITE_ROLES = ["owner", "admin", "member"] as const;
const ADMIN_ROLES = ["owner", "admin"] as const;

function utcWindow(): { dayStart: string; monthStart: string; now: string } {
  const now = new Date();
  return {
    dayStart: new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate())).toISOString(),
    monthStart: new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1)).toISOString(),
    now: now.toISOString(),
  };
}

function enumValue(value: unknown, field: string, allowed: readonly string[]): string {
  const parsed = requireString(value, field, 40);
  if (!allowed.includes(parsed)) throw new ApiError(400, "INVALID_ENUM", `${field} is invalid.`, { allowed });
  return parsed;
}

async function assertRelated(
  db: D1Database,
  table: "customers" | "leads" | "jobs",
  id: string | null,
  businessId: string,
  field: string,
): Promise<void> {
  if (!id) return;
  const row = await db
    .prepare(`SELECT id FROM ${table} WHERE id = ? AND business_id = ?`)
    .bind(id, businessId)
    .first<{ id: string }>();
  if (!row) throw new ApiError(400, "RELATED_RECORD_NOT_FOUND", `${field} does not belong to this business.`);
}

async function businessWriteContext(c: Context<AppEnvironment>): Promise<{
  db: D1Database;
  userId: string;
  businessId: string;
}> {
  const db = database(c);
  const userId = currentUserId(c);
  const businessId = requireString(c.req.param("businessId"), "businessId", 128);
  await requireBusinessRole(db, userId, businessId, WRITE_ROLES);
  return { db, userId, businessId };
}

export function registerBusinessRoutes(app: Hono<AppEnvironment>): void {
  app.post("/v1/businesses", async (c) => {
    const userId = currentUserId(c);
    return withIdempotency(c, userId, async () => {
      const body = await readJsonObject(c);
      const name = requireString(body.name, "name", 200);
      const currency = (optionalString(body.currency, "currency", 3) ?? "USD").toUpperCase();
      if (!/^[A-Z]{3}$/.test(currency)) throw new ApiError(400, "INVALID_CURRENCY", "currency must be a three-letter ISO code.");
      const timezone = optionalString(body.timezone, "timezone", 100) ?? "UTC";
      try {
        new Intl.DateTimeFormat("en", { timeZone: timezone }).format(new Date());
      } catch {
        throw new ApiError(400, "INVALID_TIMEZONE", "timezone must be a valid IANA time zone.");
      }
      const db = database(c);
      const id = crypto.randomUUID();
      const now = new Date().toISOString();
      await db.batch([
        db
          .prepare(
            `INSERT INTO businesses (id, owner_user_id, name, currency, timezone, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?)`,
          )
          .bind(id, userId, name, currency, timezone, now, now),
        db
          .prepare(
            `INSERT INTO business_memberships (business_id, user_id, role, created_at)
             VALUES (?, ?, 'owner', ?)`,
          )
          .bind(id, userId, now),
      ]);
      await writeAudit(db, {
        actorUserId: userId,
        actorType: "user",
        action: "business.create",
        targetType: "business",
        targetId: id,
        outcome: "success",
        requestId: c.get("requestId"),
      });
      return success(c, { business: { id, name, currency, timezone, role: "owner", createdAt: now } }, 201);
    });
  });

  app.get("/v1/businesses", async (c) => {
    const rows = await database(c)
      .prepare(
        `SELECT b.id, b.name, b.currency, b.timezone, m.role, b.created_at, b.updated_at
         FROM businesses b
         JOIN business_memberships m ON m.business_id = b.id
         WHERE m.user_id = ? ORDER BY b.created_at DESC LIMIT 100`,
      )
      .bind(currentUserId(c))
      .all();
    return success(c, { businesses: rows.results });
  });

  app.get("/v1/businesses/:businessId/dashboard", async (c) => {
    const db = database(c);
    const userId = currentUserId(c);
    const businessId = c.req.param("businessId");
    await requireBusinessRole(db, userId, businessId, READ_ROLES);
    const window = utcWindow();
    const metrics = await db
      .prepare(
        `SELECT
          (SELECT COALESCE(SUM(p.amount_cents), 0)
             FROM invoice_payments p JOIN invoices i ON i.id = p.invoice_id
            WHERE i.business_id = ? AND p.verification_state = 'provider_verified' AND p.paid_at >= ?) AS today_revenue_cents,
          (SELECT COALESCE(SUM(p.amount_cents), 0)
             FROM invoice_payments p JOIN invoices i ON i.id = p.invoice_id
            WHERE i.business_id = ? AND p.verification_state = 'provider_verified' AND p.paid_at >= ?) AS monthly_revenue_cents,
          (SELECT COALESCE(SUM(MAX(i.total_cents - COALESCE(paid.total_paid, 0), 0)), 0)
             FROM invoices i
             LEFT JOIN (SELECT invoice_id, SUM(amount_cents) AS total_paid FROM invoice_payments
                         WHERE verification_state = 'provider_verified' GROUP BY invoice_id) paid
               ON paid.invoice_id = i.id
            WHERE i.business_id = ? AND i.status NOT IN ('draft', 'void', 'paid')) AS outstanding_invoices_cents,
          (SELECT COUNT(*) FROM leads WHERE business_id = ? AND created_at >= ?) AS new_leads,
          (SELECT COUNT(*) FROM leads WHERE business_id = ? AND status = 'won') AS won_leads,
          (SELECT COUNT(*) FROM leads WHERE business_id = ?) AS total_leads,
          (SELECT COUNT(*) FROM jobs WHERE business_id = ? AND status = 'scheduled') AS jobs_scheduled,
          (SELECT COUNT(*) FROM jobs WHERE business_id = ? AND status = 'completed' AND completed_at >= ?) AS jobs_completed,
          (SELECT COALESCE(ROUND(AVG(total_cents)), 0) FROM invoices WHERE business_id = ? AND status = 'paid') AS average_ticket_cents,
          (SELECT COUNT(*) FROM customers WHERE business_id = ? AND created_at >= ?) AS customers_acquired,
          (SELECT COUNT(*) FROM invoices i
            WHERE i.business_id = ? AND i.status IN ('sent', 'partial', 'overdue') AND i.due_at IS NOT NULL AND i.due_at < ?) AS overdue_invoice_count`,
      )
      .bind(
        businessId, window.dayStart,
        businessId, window.monthStart,
        businessId,
        businessId, window.monthStart,
        businessId,
        businessId,
        businessId,
        businessId, window.monthStart,
        businessId,
        businessId, window.monthStart,
        businessId, window.now,
      )
      .first<Record<string, number>>();
    if (!metrics) throw new ApiError(503, "DASHBOARD_UNAVAILABLE", "Dashboard metrics could not be derived.");
    const totalLeads = Number(metrics.total_leads ?? 0);
    const wonLeads = Number(metrics.won_leads ?? 0);
    const leadConversionRate = totalLeads === 0 ? null : wonLeads / totalLeads;
    return success(c, {
      source: "D1_STORED_ROWS",
      calculatedAt: window.now,
      metricWindow: { timezone: "UTC", dayStart: window.dayStart, monthStart: window.monthStart },
      metrics: {
        todayRevenueCents: Number(metrics.today_revenue_cents ?? 0),
        monthlyRevenueCents: Number(metrics.monthly_revenue_cents ?? 0),
        outstandingInvoicesCents: Number(metrics.outstanding_invoices_cents ?? 0),
        newLeads: Number(metrics.new_leads ?? 0),
        leadConversionRate,
        jobsScheduled: Number(metrics.jobs_scheduled ?? 0),
        jobsCompleted: Number(metrics.jobs_completed ?? 0),
        averageTicketCents: Number(metrics.average_ticket_cents ?? 0),
        customersAcquired: Number(metrics.customers_acquired ?? 0),
        customerAcquisitionCostCents: null,
      },
      recommendations: {
        status: "NOT_REQUESTED",
        items: [],
        overdueInvoiceCount: Number(metrics.overdue_invoice_count ?? 0),
      },
      unavailableMetrics: ["customerAcquisitionCostCents"],
    });
  });

  app.post("/v1/businesses/:businessId/customers", async (c) => {
    const userId = currentUserId(c);
    return withIdempotency(c, userId, async () => {
      const { db, businessId } = await businessWriteContext(c);
      const body = await readJsonObject(c);
      const fullName = requireString(body.fullName, "fullName", 200);
      const email = optionalString(body.email, "email", 320);
      const phone = optionalString(body.phone, "phone", 50);
      const notes = optionalString(body.notes, "notes", 5_000);
      const address = body.address === undefined || body.address === null ? null : JSON.stringify(body.address);
      if (address && address.length > 5_000) throw new ApiError(400, "ADDRESS_TOO_LARGE", "address is too large.");
      const id = crypto.randomUUID();
      const now = new Date().toISOString();
      await db
        .prepare(
          `INSERT INTO customers (id, business_id, full_name, email, phone, address_json, notes, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        )
        .bind(id, businessId, fullName, email, phone, address, notes, now, now)
        .run();
      await writeAudit(db, {
        actorUserId: userId,
        actorType: "user",
        action: "customer.create",
        targetType: "customer",
        targetId: id,
        outcome: "success",
        requestId: c.get("requestId"),
        metadata: { businessId },
      });
      return success(c, { customer: { id, businessId, fullName, email, phone, address: body.address ?? null, notes, createdAt: now } }, 201);
    });
  });

  app.get("/v1/businesses/:businessId/customers", async (c) => {
    const db = database(c);
    const businessId = c.req.param("businessId");
    await requireBusinessRole(db, currentUserId(c), businessId, READ_ROLES);
    const rows = await db
      .prepare(
        `SELECT id, full_name, email, phone, address_json, notes, created_at, updated_at
         FROM customers WHERE business_id = ? ORDER BY created_at DESC LIMIT 100`,
      )
      .bind(businessId)
      .all();
    return success(c, { customers: rows.results });
  });

  app.post("/v1/businesses/:businessId/leads", async (c) => {
    const userId = currentUserId(c);
    return withIdempotency(c, userId, async () => {
      const { db, businessId } = await businessWriteContext(c);
      const body = await readJsonObject(c);
      const customerId = optionalString(body.customerId, "customerId", 128);
      await assertRelated(db, "customers", customerId, businessId, "customerId");
      const title = requireString(body.title, "title", 300);
      const source = optionalString(body.source, "source", 120);
      const status = body.status === undefined
        ? "new"
        : enumValue(body.status, "status", ["new", "contacted", "qualified", "won", "lost"]);
      const estimatedValueCents = requireNonNegativeInteger(body.estimatedValueCents ?? 0, "estimatedValueCents");
      const id = crypto.randomUUID();
      const now = new Date().toISOString();
      await db
        .prepare(
          `INSERT INTO leads
           (id, business_id, customer_id, title, source, status, estimated_value_cents, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        )
        .bind(id, businessId, customerId, title, source, status, estimatedValueCents, now, now)
        .run();
      await writeAudit(db, {
        actorUserId: userId,
        actorType: "user",
        action: "lead.create",
        targetType: "lead",
        targetId: id,
        outcome: "success",
        requestId: c.get("requestId"),
        metadata: { businessId },
      });
      return success(c, { lead: { id, businessId, customerId, title, source, status, estimatedValueCents, createdAt: now } }, 201);
    });
  });

  app.get("/v1/businesses/:businessId/leads", async (c) => {
    const db = database(c);
    const businessId = c.req.param("businessId");
    await requireBusinessRole(db, currentUserId(c), businessId, READ_ROLES);
    const rows = await db
      .prepare(
        `SELECT id, customer_id, title, source, status, estimated_value_cents, created_at, updated_at
         FROM leads WHERE business_id = ? ORDER BY created_at DESC LIMIT 100`,
      )
      .bind(businessId)
      .all();
    return success(c, { leads: rows.results });
  });

  app.post("/v1/businesses/:businessId/jobs", async (c) => {
    const userId = currentUserId(c);
    return withIdempotency(c, userId, async () => {
      const { db, businessId } = await businessWriteContext(c);
      const body = await readJsonObject(c);
      const customerId = optionalString(body.customerId, "customerId", 128);
      const leadId = optionalString(body.leadId, "leadId", 128);
      await Promise.all([
        assertRelated(db, "customers", customerId, businessId, "customerId"),
        assertRelated(db, "leads", leadId, businessId, "leadId"),
      ]);
      const title = requireString(body.title, "title", 300);
      const description = optionalString(body.description, "description", 10_000);
      const status = body.status === undefined
        ? "draft"
        : enumValue(body.status, "status", ["draft", "scheduled", "in_progress", "completed", "cancelled"]);
      const scheduledStart = optionalString(body.scheduledStart, "scheduledStart", 40);
      const scheduledEnd = optionalString(body.scheduledEnd, "scheduledEnd", 40);
      const customerPriceCents = requireNonNegativeInteger(body.customerPriceCents ?? 0, "customerPriceCents");
      const materialsCostCents = requireNonNegativeInteger(body.materialsCostCents ?? 0, "materialsCostCents");
      const laborCostCents = requireNonNegativeInteger(body.laborCostCents ?? 0, "laborCostCents");
      const travelCostCents = requireNonNegativeInteger(body.travelCostCents ?? 0, "travelCostCents");
      const id = crypto.randomUUID();
      const now = new Date().toISOString();
      await db
        .prepare(
          `INSERT INTO jobs
           (id, business_id, customer_id, lead_id, title, description, status, scheduled_start, scheduled_end,
            customer_price_cents, materials_cost_cents, labor_cost_cents, travel_cost_cents, created_at, updated_at, completed_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        )
        .bind(
          id, businessId, customerId, leadId, title, description, status, scheduledStart, scheduledEnd,
          customerPriceCents, materialsCostCents, laborCostCents, travelCostCents, now, now,
          status === "completed" ? now : null,
        )
        .run();
      const estimatedProfitCents = customerPriceCents - materialsCostCents - laborCostCents - travelCostCents;
      await writeAudit(db, {
        actorUserId: userId,
        actorType: "user",
        action: "job.create",
        targetType: "job",
        targetId: id,
        outcome: "success",
        requestId: c.get("requestId"),
        metadata: { businessId },
      });
      return success(c, {
        job: {
          id, businessId, customerId, leadId, title, description, status, scheduledStart, scheduledEnd,
          customerPriceCents, materialsCostCents, laborCostCents, travelCostCents, estimatedProfitCents, createdAt: now,
        },
      }, 201);
    });
  });

  app.get("/v1/businesses/:businessId/jobs", async (c) => {
    const db = database(c);
    const businessId = c.req.param("businessId");
    await requireBusinessRole(db, currentUserId(c), businessId, READ_ROLES);
    const rows = await db
      .prepare(
        `SELECT id, customer_id, lead_id, title, description, status, scheduled_start, scheduled_end,
                customer_price_cents, materials_cost_cents, labor_cost_cents, travel_cost_cents,
                (customer_price_cents - materials_cost_cents - labor_cost_cents - travel_cost_cents) AS estimated_profit_cents,
                created_at, updated_at, completed_at
         FROM jobs WHERE business_id = ? ORDER BY created_at DESC LIMIT 100`,
      )
      .bind(businessId)
      .all();
    return success(c, { jobs: rows.results });
  });

  async function createFinancialDocument(c: Context<AppEnvironment>, kind: "estimate" | "invoice"): Promise<Response> {
    const userId = currentUserId(c);
    return withIdempotency(c, userId, async () => {
      const { db, businessId } = await businessWriteContext(c);
      const body = await readJsonObject(c);
      const customerId = optionalString(body.customerId, "customerId", 128);
      const jobId = optionalString(body.jobId, "jobId", 128);
      await Promise.all([
        assertRelated(db, "customers", customerId, businessId, "customerId"),
        assertRelated(db, "jobs", jobId, businessId, "jobId"),
      ]);
      const business = await db
        .prepare("SELECT currency FROM businesses WHERE id = ?")
        .bind(businessId)
        .first<{ currency: string }>();
      if (!business) throw new ApiError(404, "BUSINESS_NOT_FOUND", "Business not found.");
      const totals = calculateDocumentTotals(body.items, body.taxRateBasisPoints);
      const notes = optionalString(body.notes, "notes", 10_000);
      const id = crypto.randomUUID();
      const now = new Date().toISOString();
      const header = kind === "estimate"
        ? db
            .prepare(
              `INSERT INTO estimates
               (id, business_id, customer_id, job_id, status, currency, subtotal_cents, tax_cents, total_cents,
                notes, valid_until, created_at, updated_at)
               VALUES (?, ?, ?, ?, 'draft', ?, ?, ?, ?, ?, ?, ?, ?)`,
            )
            .bind(
              id, businessId, customerId, jobId, business.currency, totals.subtotalCents, totals.taxCents,
              totals.totalCents, notes, optionalString(body.validUntil, "validUntil", 40), now, now,
            )
        : db
            .prepare(
              `INSERT INTO invoices
               (id, business_id, customer_id, job_id, status, currency, subtotal_cents, tax_cents, total_cents,
                due_at, notes, created_at, updated_at)
               VALUES (?, ?, ?, ?, 'draft', ?, ?, ?, ?, ?, ?, ?, ?)`,
            )
            .bind(
              id, businessId, customerId, jobId, business.currency, totals.subtotalCents, totals.taxCents,
              totals.totalCents, optionalString(body.dueAt, "dueAt", 40), notes, now, now,
            );
      const table = kind === "estimate" ? "estimate_items" : "invoice_items";
      const foreignKey = kind === "estimate" ? "estimate_id" : "invoice_id";
      const items = totals.items.map((item) =>
        db
          .prepare(
            `INSERT INTO ${table}
             (id, ${foreignKey}, description, quantity_millis, unit_price_cents, line_total_cents)
             VALUES (?, ?, ?, ?, ?, ?)`,
          )
          .bind(crypto.randomUUID(), id, item.description, item.quantityMillis, item.unitPriceCents, item.lineTotalCents),
      );
      await db.batch([header, ...items]);
      await writeAudit(db, {
        actorUserId: userId,
        actorType: "user",
        action: `${kind}.create`,
        targetType: kind,
        targetId: id,
        outcome: "success",
        requestId: c.get("requestId"),
        metadata: { businessId, totalCents: totals.totalCents },
      });
      return success(c, {
        [kind]: {
          id,
          businessId,
          customerId,
          jobId,
          status: "draft",
          currency: business.currency,
          ...totals,
          notes,
          createdAt: now,
          externalDelivery: { state: "NOT_SENT" },
          paymentState: kind === "invoice" ? "UNPAID" : undefined,
        },
      }, 201);
    });
  }

  app.post("/v1/businesses/:businessId/estimates", (c) => createFinancialDocument(c, "estimate"));
  app.post("/v1/businesses/:businessId/invoices", (c) => createFinancialDocument(c, "invoice"));

  for (const kind of ["estimates", "invoices"] as const) {
    app.get(`/v1/businesses/:businessId/${kind}`, async (c) => {
      const db = database(c);
      const businessId = c.req.param("businessId");
      await requireBusinessRole(db, currentUserId(c), businessId, READ_ROLES);
      const rows = await db
        .prepare(
          `SELECT id, customer_id, job_id, status, currency, subtotal_cents, tax_cents, total_cents,
                  created_at, updated_at
           FROM ${kind} WHERE business_id = ? ORDER BY created_at DESC LIMIT 100`,
        )
        .bind(businessId)
        .all();
      return success(c, { [kind]: d1Rows(rows) });
    });
  }

  app.post("/v1/businesses/:businessId/invoices/:invoiceId/payments", async (c) => {
    const userId = currentUserId(c);
    return withIdempotency(c, userId, async () => {
      const { db, businessId } = await businessWriteContext(c);
      await requireBusinessRole(db, userId, businessId, ADMIN_ROLES);
      const invoice = await db
        .prepare("SELECT id FROM invoices WHERE id = ? AND business_id = ?")
        .bind(c.req.param("invoiceId"), businessId)
        .first();
      if (!invoice) throw new ApiError(404, "INVOICE_NOT_FOUND", "Invoice not found.");
      throw new ApiError(
        503,
        "PAYMENTS_DISCONNECTED",
        "No customer-payment provider webhook is configured. A client request cannot create verified revenue.",
      );
    });
  });
}
