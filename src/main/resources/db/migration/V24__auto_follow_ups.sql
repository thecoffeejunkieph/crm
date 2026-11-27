ALTER TABLE follow_up
  ADD COLUMN rule text CHECK (rule IN ('RESTOCK','WIN_BACK','QUOTE_CHASE','QUOTE_EXPIRING','QUOTE_DECLINED',
                                       'FIRST_ORDER','PAYMENT_OVERDUE','DEAL_STALLED')),
  ADD COLUMN auto_key text UNIQUE,
  ADD COLUMN closed_reason text,
  ADD COLUMN overdue_notified_at timestamptz,
  ALTER COLUMN created_by DROP NOT NULL;

CREATE INDEX follow_up_open_rule_idx ON follow_up (rule) WHERE done_at IS NULL AND rule IS NOT NULL;

CREATE TABLE auto_follow_up_run (day date PRIMARY KEY);
