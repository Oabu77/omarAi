CREATE TABLE ai_output_reports (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  task_id TEXT NOT NULL,
  provider TEXT NOT NULL,
  model_id TEXT,
  output_text TEXT NOT NULL,
  category TEXT,
  status TEXT NOT NULL CHECK (status IN ('received', 'reviewing', 'resolved', 'dismissed')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);

CREATE INDEX ai_output_reports_user_idx ON ai_output_reports(user_id, created_at);
CREATE INDEX ai_output_reports_task_idx ON ai_output_reports(task_id, created_at);
