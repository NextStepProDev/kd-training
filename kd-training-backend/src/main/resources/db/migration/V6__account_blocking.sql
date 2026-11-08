-- End of cooperation (the client's answer "b albo c"): the coach blocks the account instead of deleting
-- it. A blocked person cannot log in, refresh a session or reset the password, and their data waits in
-- case they come back; deleting everything stays a separate, explicit action. Null = active.
ALTER TABLE users ADD COLUMN blocked_at TIMESTAMPTZ;
