PRAGMA foreign_keys = ON;

CREATE TABLE users (
  id TEXT PRIMARY KEY,
  email TEXT,
  display_name TEXT,
  preferred_language TEXT NOT NULL DEFAULT 'en',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE businesses (
  id TEXT PRIMARY KEY,
  owner_user_id TEXT NOT NULL,
  name TEXT NOT NULL,
  currency TEXT NOT NULL DEFAULT 'USD',
  timezone TEXT NOT NULL DEFAULT 'UTC',
  settings_json TEXT NOT NULL DEFAULT '{}',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE business_memberships (
  business_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('owner', 'admin', 'member', 'viewer')),
  created_at TEXT NOT NULL,
  PRIMARY KEY (business_id, user_id),
  FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE assistant_messages (
  id TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
  content TEXT NOT NULL,
  model_provider TEXT,
  model_id TEXT,
  created_at TEXT NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX assistant_messages_conversation_idx
  ON assistant_messages(user_id, conversation_id, created_at);

CREATE TABLE tasks (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  business_id TEXT,
  conversation_id TEXT,
  title TEXT NOT NULL,
  intent TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN (
    'planning', 'planned', 'waiting_approval', 'queued', 'running',
    'completed', 'failed', 'cancelled'
  )),
  verification_state TEXT NOT NULL DEFAULT 'not_executed' CHECK (verification_state IN (
    'not_executed', 'unverified', 'provider_verified', 'failed'
  )),
  cancellable INTEGER NOT NULL DEFAULT 1 CHECK (cancellable IN (0, 1)),
  error_code TEXT,
  error_message TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  started_at TEXT,
  completed_at TEXT,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

CREATE INDEX tasks_user_status_idx ON tasks(user_id, status, created_at);
CREATE INDEX tasks_business_idx ON tasks(business_id, created_at);

CREATE TABLE task_steps (
  id TEXT PRIMARY KEY,
  task_id TEXT NOT NULL,
  ordinal INTEGER NOT NULL,
  agent TEXT NOT NULL,
  action TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN (
    'planned', 'waiting_approval', 'queued', 'running', 'completed', 'failed', 'cancelled'
  )),
  requires_approval INTEGER NOT NULL DEFAULT 0 CHECK (requires_approval IN (0, 1)),
  external_action INTEGER NOT NULL DEFAULT 0 CHECK (external_action IN (0, 1)),
  result_summary TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE (task_id, ordinal),
  FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);

CREATE TABLE approvals (
  id TEXT PRIMARY KEY,
  task_id TEXT NOT NULL,
  step_id TEXT,
  user_id TEXT NOT NULL,
  decision TEXT NOT NULL CHECK (decision IN ('approved', 'denied')),
  scope_json TEXT NOT NULL,
  decided_at TEXT NOT NULL,
  FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
  FOREIGN KEY (step_id) REFERENCES task_steps(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX approvals_task_idx ON approvals(task_id, decided_at);

CREATE TABLE task_reports (
  id TEXT PRIMARY KEY,
  task_id TEXT NOT NULL,
  reporter_type TEXT NOT NULL CHECK (reporter_type IN ('ai_provider', 'executor', 'client')),
  provider TEXT NOT NULL,
  model_id TEXT,
  output_text TEXT NOT NULL,
  reported_state TEXT NOT NULL CHECK (reported_state IN (
    'prepared', 'submitted', 'completed', 'failed'
  )),
  evidence_state TEXT NOT NULL CHECK (evidence_state IN (
    'none', 'client_supplied', 'provider_verified', 'rejected'
  )),
  provider_reference TEXT,
  evidence_json TEXT,
  created_at TEXT NOT NULL,
  FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);

CREATE INDEX task_reports_task_idx ON task_reports(task_id, created_at);

CREATE TABLE customers (
  id TEXT PRIMARY KEY,
  business_id TEXT NOT NULL,
  full_name TEXT NOT NULL,
  email TEXT,
  phone TEXT,
  address_json TEXT,
  notes TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

CREATE INDEX customers_business_idx ON customers(business_id, created_at);

CREATE TABLE leads (
  id TEXT PRIMARY KEY,
  business_id TEXT NOT NULL,
  customer_id TEXT,
  title TEXT NOT NULL,
  source TEXT,
  status TEXT NOT NULL CHECK (status IN ('new', 'contacted', 'qualified', 'won', 'lost')),
  estimated_value_cents INTEGER NOT NULL DEFAULT 0 CHECK (estimated_value_cents >= 0),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL
);

CREATE INDEX leads_business_status_idx ON leads(business_id, status, created_at);

CREATE TABLE jobs (
  id TEXT PRIMARY KEY,
  business_id TEXT NOT NULL,
  customer_id TEXT,
  lead_id TEXT,
  title TEXT NOT NULL,
  description TEXT,
  status TEXT NOT NULL CHECK (status IN ('draft', 'scheduled', 'in_progress', 'completed', 'cancelled')),
  scheduled_start TEXT,
  scheduled_end TEXT,
  customer_price_cents INTEGER NOT NULL DEFAULT 0 CHECK (customer_price_cents >= 0),
  materials_cost_cents INTEGER NOT NULL DEFAULT 0 CHECK (materials_cost_cents >= 0),
  labor_cost_cents INTEGER NOT NULL DEFAULT 0 CHECK (labor_cost_cents >= 0),
  travel_cost_cents INTEGER NOT NULL DEFAULT 0 CHECK (travel_cost_cents >= 0),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  completed_at TEXT,
  FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL,
  FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE SET NULL
);

CREATE INDEX jobs_business_status_idx ON jobs(business_id, status, scheduled_start);

CREATE TABLE estimates (
  id TEXT PRIMARY KEY,
  business_id TEXT NOT NULL,
  customer_id TEXT,
  job_id TEXT,
  status TEXT NOT NULL CHECK (status IN ('draft', 'sent', 'accepted', 'declined', 'expired')),
  currency TEXT NOT NULL,
  subtotal_cents INTEGER NOT NULL CHECK (subtotal_cents >= 0),
  tax_cents INTEGER NOT NULL CHECK (tax_cents >= 0),
  total_cents INTEGER NOT NULL CHECK (total_cents >= 0),
  notes TEXT,
  valid_until TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL,
  FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE SET NULL
);

CREATE TABLE estimate_items (
  id TEXT PRIMARY KEY,
  estimate_id TEXT NOT NULL,
  description TEXT NOT NULL,
  quantity_millis INTEGER NOT NULL CHECK (quantity_millis > 0),
  unit_price_cents INTEGER NOT NULL CHECK (unit_price_cents >= 0),
  line_total_cents INTEGER NOT NULL CHECK (line_total_cents >= 0),
  FOREIGN KEY (estimate_id) REFERENCES estimates(id) ON DELETE CASCADE
);

CREATE INDEX estimates_business_idx ON estimates(business_id, created_at);

CREATE TABLE invoices (
  id TEXT PRIMARY KEY,
  business_id TEXT NOT NULL,
  customer_id TEXT,
  job_id TEXT,
  status TEXT NOT NULL CHECK (status IN ('draft', 'sent', 'partial', 'paid', 'overdue', 'void')),
  currency TEXT NOT NULL,
  subtotal_cents INTEGER NOT NULL CHECK (subtotal_cents >= 0),
  tax_cents INTEGER NOT NULL CHECK (tax_cents >= 0),
  total_cents INTEGER NOT NULL CHECK (total_cents >= 0),
  due_at TEXT,
  paid_at TEXT,
  notes TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
  FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL,
  FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE SET NULL
);

CREATE TABLE invoice_items (
  id TEXT PRIMARY KEY,
  invoice_id TEXT NOT NULL,
  description TEXT NOT NULL,
  quantity_millis INTEGER NOT NULL CHECK (quantity_millis > 0),
  unit_price_cents INTEGER NOT NULL CHECK (unit_price_cents >= 0),
  line_total_cents INTEGER NOT NULL CHECK (line_total_cents >= 0),
  FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE
);

CREATE TABLE invoice_payments (
  id TEXT PRIMARY KEY,
  invoice_id TEXT NOT NULL,
  amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
  provider TEXT NOT NULL,
  provider_reference TEXT NOT NULL,
  verification_state TEXT NOT NULL CHECK (verification_state IN ('provider_verified', 'refunded')),
  paid_at TEXT NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE (provider, provider_reference),
  FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE
);

CREATE INDEX invoices_business_status_idx ON invoices(business_id, status, due_at);
CREATE INDEX invoice_payments_date_idx ON invoice_payments(paid_at);

CREATE TABLE entitlements (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  provider TEXT NOT NULL,
  product_id TEXT NOT NULL,
  entitlement_key TEXT NOT NULL,
  state TEXT NOT NULL CHECK (state IN ('active', 'grace_period', 'expired', 'revoked')),
  purchase_token_hash TEXT NOT NULL,
  provider_transaction_id TEXT NOT NULL,
  verified_at TEXT NOT NULL,
  expires_at TEXT,
  updated_at TEXT NOT NULL,
  UNIQUE (provider, provider_transaction_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX entitlements_user_idx ON entitlements(user_id, state, expires_at);

CREATE TABLE billing_verifications (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  provider TEXT NOT NULL,
  product_id TEXT NOT NULL,
  purchase_token_hash TEXT NOT NULL,
  outcome TEXT NOT NULL CHECK (outcome IN ('verified', 'rejected', 'provider_error')),
  provider_reference TEXT,
  created_at TEXT NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE audit_logs (
  id TEXT PRIMARY KEY,
  actor_user_id TEXT,
  actor_type TEXT NOT NULL CHECK (actor_type IN ('user', 'service', 'system')),
  action TEXT NOT NULL,
  target_type TEXT NOT NULL,
  target_id TEXT,
  outcome TEXT NOT NULL CHECK (outcome IN ('success', 'failure', 'denied')),
  request_id TEXT NOT NULL,
  metadata_json TEXT NOT NULL DEFAULT '{}',
  created_at TEXT NOT NULL
);

CREATE INDEX audit_logs_actor_idx ON audit_logs(actor_user_id, created_at);
CREATE INDEX audit_logs_target_idx ON audit_logs(target_type, target_id, created_at);

CREATE TABLE idempotency_keys (
  user_id TEXT NOT NULL,
  route TEXT NOT NULL,
  idempotency_key TEXT NOT NULL,
  request_hash TEXT NOT NULL,
  state TEXT NOT NULL CHECK (state IN ('processing', 'completed')),
  response_status INTEGER,
  response_body TEXT,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  PRIMARY KEY (user_id, route, idempotency_key),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE rate_limits (
  subject_key TEXT NOT NULL,
  scope TEXT NOT NULL,
  window_start INTEGER NOT NULL,
  request_count INTEGER NOT NULL,
  PRIMARY KEY (subject_key, scope, window_start)
);

CREATE INDEX rate_limits_window_idx ON rate_limits(window_start);
