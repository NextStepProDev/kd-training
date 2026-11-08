-- The coach's days off (the client's answer: holidays marked in the schedule, so clients see a session
-- will not take place, "to byłoby ok"). One coach, so a day off belongs to nobody in particular: every
-- client sees it in their own calendar. No FK to users — the note is the coach's announcement, not
-- anybody's personal data, and it must stay out of the GDPR export.
--
-- A range, both ends inclusive: a week's holiday is one row, not seven.
CREATE TABLE coach_days_off (
    id         UUID PRIMARY KEY,
    start_date DATE NOT NULL,
    end_date   DATE NOT NULL,
    note       VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_days_off_range CHECK (end_date >= start_date)
);

CREATE INDEX idx_days_off_range ON coach_days_off (start_date, end_date);
