-- Materials: what the coach gives one client to keep, independent of any date (the client's answer:
-- "na stałe", one set per person). A clip from the library, a written tip, or both.
--
-- The video is RESTRICT like training_attachments.video_id: a clip in use cannot be deleted from the
-- library, only archived, and the material keeps showing it.
CREATE TABLE athlete_materials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    note VARCHAR(2000),
    video_id UUID REFERENCES exercise_videos(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A title alone gives the client nothing to look at.
    CONSTRAINT chk_am_content CHECK (note IS NOT NULL OR video_id IS NOT NULL)
);

CREATE INDEX idx_am_athlete ON athlete_materials (athlete_id, created_at);
CREATE INDEX idx_am_video ON athlete_materials (video_id);

-- Questions asked at a material (the client's answer: "pytanie przy materiale"), and the coach's
-- replies. Text only: photos stay on the training comments, where their 30-day retention lives.
CREATE TABLE material_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    material_id UUID NOT NULL REFERENCES athlete_materials(id) ON DELETE CASCADE,
    -- Like training_comments: the author may delete their account, the thread stays for the other side.
    author_id UUID REFERENCES users(id) ON DELETE SET NULL,
    -- Frozen at the time of writing, for the same reason as training_comments.author_is_admin.
    author_is_admin BOOLEAN NOT NULL,
    body VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mq_material ON material_questions (material_id, created_at);
