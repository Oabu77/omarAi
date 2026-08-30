import { describe, expect, it } from "vitest";
import { isValidIdempotencyKey } from "../src/idempotency";
import { isDeletionConfirmed } from "../src/routes/account";

describe("Android mutation contract", () => {
  it("accepts UUID idempotency keys and rejects missing/unsafe keys", () => {
    expect(isValidIdempotencyKey("11b8dc63-6e8c-455a-a831-e8b29848e9af")).toBe(true);
    expect(isValidIdempotencyKey(undefined)).toBe(false);
    expect(isValidIdempotencyKey("short")).toBe(false);
    expect(isValidIdempotencyKey("invalid key with spaces")).toBe(false);
  });

  it("requires both the exact deletion header and JSON confirmation", () => {
    expect(isDeletionConfirmed("DELETE OMAR AI ACCOUNT", { confirm: true })).toBe(true);
    expect(isDeletionConfirmed("DELETE OMAR AI ACCOUNT", { confirm: false })).toBe(false);
    expect(isDeletionConfirmed("delete omar ai account", { confirm: true })).toBe(false);
  });
});
