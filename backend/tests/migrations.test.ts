import { readFileSync, readdirSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import { describe, expect, it } from "vitest";

function migratedDatabase(): DatabaseSync {
  const database = new DatabaseSync(":memory:");
  for (const file of readdirSync("migrations").sort()) {
    database.exec(readFileSync(`migrations/${file}`, "utf8"));
  }
  return database;
}

describe("D1-compatible schema security invariants", () => {
  it("binds a Google Play transaction and token to one entitlement identity", () => {
    const database = migratedDatabase();
    const now = new Date().toISOString();
    database.prepare("INSERT INTO users (id, created_at, updated_at) VALUES (?, ?, ?)").run("user-a", now, now);
    database.prepare("INSERT INTO users (id, created_at, updated_at) VALUES (?, ?, ?)").run("user-b", now, now);
    database.prepare(
      `INSERT INTO entitlements
       (id, user_id, provider, product_id, entitlement_key, state, purchase_token_hash,
        provider_transaction_id, verified_at, updated_at)
       VALUES (?, ?, 'google-play', ?, ?, 'pending_activation', ?, ?, ?, ?)`,
    ).run("entitlement-a", "user-a", "pro", "pro", "token-hash-a", "transaction-a", now, now);

    expect(() => database.prepare(
      `INSERT INTO entitlements
       (id, user_id, provider, product_id, entitlement_key, state, purchase_token_hash,
        provider_transaction_id, verified_at, updated_at)
       VALUES (?, ?, 'google-play', ?, ?, 'pending_activation', ?, ?, ?, ?)`,
    ).run("entitlement-b", "user-b", "pro", "pro", "token-hash-a", "transaction-b", now, now)).toThrow();

    expect(() => database.prepare(
      `INSERT INTO entitlements
       (id, user_id, provider, product_id, entitlement_key, state, purchase_token_hash,
        provider_transaction_id, verified_at, updated_at)
       VALUES (?, ?, 'google-play', ?, ?, 'pending_activation', ?, ?, ?, ?)`,
    ).run("entitlement-c", "user-b", "pro", "pro", "token-hash-c", "transaction-a", now, now)).toThrow();
    database.close();
  });

  it("keeps moderation reports separate from task execution reports", () => {
    const database = migratedDatabase();
    const tables = database.prepare(
      "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('ai_output_reports', 'task_reports') ORDER BY name",
    ).all() as Array<{ name: string }>;
    expect(tables.map((row) => row.name)).toEqual(["ai_output_reports", "task_reports"]);
    database.close();
  });
});
