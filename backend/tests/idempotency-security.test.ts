import { describe, expect, it, vi } from "vitest";
import { clearExpiredIdempotencyKey } from "../src/idempotency";

describe("idempotency expiry boundary", () => {
  it("removes only the caller's exact expired key before reuse", async () => {
    let preparedSql = "";
    let boundValues: unknown[] = [];
    const run = vi.fn(async () => ({ meta: { changes: 1 } }));
    const db = {
      prepare(sql: string) {
        preparedSql = sql;
        return {
          bind(...values: unknown[]) {
            boundValues = values;
            return { run };
          },
        };
      },
    } as unknown as D1Database;

    await clearExpiredIdempotencyKey(
      db,
      "user-synthetic-1",
      "POST /v1/tasks/plan",
      "synthetic-key-1234",
      "2026-09-12T00:00:00.000Z",
    );

    expect(preparedSql).toContain("DELETE FROM idempotency_keys");
    expect(preparedSql).toContain("user_id = ?");
    expect(preparedSql).toContain("route = ?");
    expect(preparedSql).toContain("idempotency_key = ?");
    expect(preparedSql).toContain("expires_at <= ?");
    expect(boundValues).toEqual([
      "user-synthetic-1",
      "POST /v1/tasks/plan",
      "synthetic-key-1234",
      "2026-09-12T00:00:00.000Z",
    ]);
    expect(run).toHaveBeenCalledTimes(1);
  });
});
