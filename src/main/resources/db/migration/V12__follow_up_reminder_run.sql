-- One row per Manila day the 08:00 follow-ups reminder went out; inserted in the same transaction as the
-- notifications, so a day sends once across instances and survives Redis being down.
CREATE TABLE follow_up_reminder_run (
  day date PRIMARY KEY,
  sent_at timestamptz NOT NULL DEFAULT now());
