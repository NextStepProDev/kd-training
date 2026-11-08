-- Weekly series (the client's answer: "powtarzalność jest mile widziana"). The coach plans a training
-- once with "repeat weekly until <date>", and every occurrence becomes an ordinary row sharing one
-- series_id. Each row stays editable on its own; the id only lets "delete this and the following ones"
-- find the rest. No table of its own: the series has nothing to remember beyond which rows belong to it.
--
-- A copy (duplicate, paste COPY) gets no series_id, so it never joins the series it was copied from.
ALTER TABLE personal_trainings ADD COLUMN series_id UUID;

CREATE INDEX idx_pt_series ON personal_trainings (series_id, training_date) WHERE series_id IS NOT NULL;
