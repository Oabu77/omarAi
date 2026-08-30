CREATE TABLE entitlements_v2 (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  provider TEXT NOT NULL,
  product_id TEXT NOT NULL,
  entitlement_key TEXT NOT NULL,
  state TEXT NOT NULL CHECK (state IN ('pending_activation', 'active', 'grace_period', 'expired', 'revoked')),
  purchase_token_hash TEXT NOT NULL,
  provider_transaction_id TEXT NOT NULL,
  verified_at TEXT NOT NULL,
  expires_at TEXT,
  updated_at TEXT NOT NULL,
  UNIQUE (provider, provider_transaction_id),
  UNIQUE (provider, purchase_token_hash),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

INSERT INTO entitlements_v2
  (id, user_id, provider, product_id, entitlement_key, state, purchase_token_hash,
   provider_transaction_id, verified_at, expires_at, updated_at)
SELECT id, user_id, provider, product_id, entitlement_key, state, purchase_token_hash,
       provider_transaction_id, verified_at, expires_at, updated_at
  FROM entitlements;

DROP TABLE entitlements;
ALTER TABLE entitlements_v2 RENAME TO entitlements;

CREATE INDEX entitlements_user_idx ON entitlements(user_id, state, expires_at);

ALTER TABLE billing_verifications ADD COLUMN acknowledgement_state TEXT;
ALTER TABLE billing_verifications ADD COLUMN lifecycle_evidence_json TEXT;
