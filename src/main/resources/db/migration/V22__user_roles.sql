-- A user can hold several roles (e.g. an admin who is also a sales rep). Each user keeps their current role.
CREATE TABLE user_role (
  user_id bigint NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  role text NOT NULL CHECK (role IN ('ADMIN','SALES','WAREHOUSE')),
  PRIMARY KEY (user_id, role));
CREATE INDEX user_role_role_idx ON user_role (role);
INSERT INTO user_role (user_id, role) SELECT id, role FROM app_user;
ALTER TABLE app_user DROP COLUMN role;
