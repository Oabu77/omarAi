CREATE TABLE account_deletion_receipts (
  subject_hash TEXT NOT NULL,
  idempotency_key_hash TEXT NOT NULL,
  response_body TEXT NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  PRIMARY KEY (subject_hash, idempotency_key_hash)
);

CREATE INDEX account_deletion_receipts_expiry_idx ON account_deletion_receipts(expires_at);
