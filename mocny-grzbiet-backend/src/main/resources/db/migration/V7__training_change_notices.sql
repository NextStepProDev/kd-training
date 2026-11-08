-- Mail to the client when the coach adds, changes or cancels a training (the client's answer: "tak").
-- Not one mail per edit: the coach often fixes an entry two or three times in a row, so changes wait
-- here until the client's calendar has been quiet for a few minutes and then go out as ONE mail.
--
-- One row per training, holding the FIRST change since the last mail: what the client last heard is
-- what the mail compares against. Later edits only move last_change_at. The training's current state
-- is read at send time, so the row needs no "after" snapshot; a cancelled training is gone by then,
-- which is why the date and time are kept here.
--
-- training_id has no foreign key on purpose: a cancellation outlives the row it is about.
-- Rows live for minutes. They are not part of the GDPR export (see UserDataExportCoverageTest).
CREATE TABLE training_change_notices (
    training_id    UUID PRIMARY KEY,
    athlete_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    change_kind    VARCHAR(20) NOT NULL,
    training_date  DATE        NOT NULL,
    start_time     TIME,
    last_change_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_change_notice_kind CHECK (change_kind IN ('ADDED', 'CHANGED', 'CANCELLED'))
);

CREATE INDEX idx_change_notices_athlete ON training_change_notices (athlete_id, last_change_at);
