-- Trainings with more than one client (the client's answer: some clients train in pairs, and the pair
-- sees each other's names at the training). Every participant gets an ordinary row of their own in their
-- own calendar; group_id ties the rows of one session together. Nothing personal is shared: completion,
-- effort rating, comments and photos stay on each person's row. The partner sees a first name, no more.
--
-- No limit of two in the schema: a third person is a third row with the same group_id.
-- A copy (duplicate to another client, paste COPY) gets no group_id, like series_id (V4).
ALTER TABLE personal_trainings ADD COLUMN group_id UUID;

CREATE INDEX idx_pt_group ON personal_trainings (group_id) WHERE group_id IS NOT NULL;
