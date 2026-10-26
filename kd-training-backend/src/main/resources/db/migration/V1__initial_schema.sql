-- Initial schema of KD Training.
--
-- Squashed from Fire Academy's V1–V42 (the project this one was forked from) with the club-only
-- features left out: events, event types, instructors, sign-ups, group training slots with their
-- billing (payments, refunds, days off, cancelled sessions) and the weight log. What remains was
-- produced by running those 42 migrations on PostgreSQL 18 and removing the dropped parts, so almost
-- every constraint below has already run in production there. Three are new, born from the cut:
-- admin_private_notes.training_id is NOT NULL and uq_apn_training is a plain UNIQUE (Fire had four
-- note targets and partial indexes), and uq_athlete_goals_active is per horizon again (Fire made it
-- per kind of goal once weight goals existed). The reasoning behind each is kept next to it.
--
-- Until the first production deploy this file may still be corrected (see MIGRATIONS.md). After it,
-- it is immutable like every applied migration: a changed comment changes the checksum and fails
-- the deploy. New changes go into V2 onwards.

-- ---------------------------------------------------------------------------------------------
-- Accounts
-- ---------------------------------------------------------------------------------------------

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    oauth_provider VARCHAR(50),
    oauth_id VARCHAR(255),
    password_hash VARCHAR(255),
    email_verified BOOLEAN NOT NULL DEFAULT false,
    email_verified_at TIMESTAMPTZ,
    preferred_language VARCHAR(5) NOT NULL DEFAULT 'pl',
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    avatar_filename VARCHAR(255),
    -- Consents are timestamps, never booleans: NULL means not given, and the moment itself is the
    -- proof GDPR asks for.
    privacy_accepted_at TIMESTAMPTZ,
    marketing_consent_at TIMESTAMPTZ,
    -- Stable, long-lived token for the unsubscribe link in marketing mail (works without logging in).
    -- Every user has one regardless of consent.
    marketing_unsubscribe_token UUID NOT NULL DEFAULT gen_random_uuid(),
    -- A 1-on-1 coaching client ("podopieczny"). Set by the coach; clearing it hides the calendar but
    -- never deletes the data behind it.
    is_athlete BOOLEAN NOT NULL DEFAULT false,
    -- Explicit consent to processing the calendar's health data (GDPR art. 9(2)(a)). Cleared together
    -- with is_athlete, so coming back after a break is a fresh decision. Widening what the calendar
    -- stores means clearing it for everybody, because consent given against the old wording cannot
    -- cover the new scope.
    training_consent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_oauth ON users (oauth_provider, oauth_id);
CREATE UNIQUE INDEX idx_users_marketing_unsubscribe_token ON users (marketing_unsubscribe_token);
-- Partial: a handful of clients among all accounts. A plain index on a boolean this skewed would
-- never be used; the partial one makes "list all clients" an index-only scan.
CREATE INDEX idx_users_athlete ON users (is_athlete) WHERE is_athlete = true;

-- Verification, password reset and refresh tokens. Only the SHA-256 of a token is stored.
CREATE TABLE auth_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    token_type VARCHAR(30) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auth_tokens_hash_type ON auth_tokens (token_hash, token_type);
CREATE INDEX idx_auth_tokens_user ON auth_tokens (user_id);

-- ---------------------------------------------------------------------------------------------
-- The 1-on-1 plan
-- ---------------------------------------------------------------------------------------------

-- The shared plan: the coach schedules, the client ticks off. One row = one entry.
--
-- MISSED is never stored: it is training_date < today AND completed_at IS NULL, computed on read.
-- No status column, no nightly job to drift out of sync.
CREATE TABLE personal_trainings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- A TASK ("stay under 2200 kcal today") is a separate ROW next to a TRAINING, never a field on
    -- one: a client can nail the session and blow the diet on the same day, and one tick box cannot
    -- say that.
    kind VARCHAR(20) NOT NULL DEFAULT 'TRAINING',
    training_date DATE NOT NULL,
    -- Both nullable, and BOTH NULL is the normal case: most entries are "do this on Wednesday", not
    -- "meet at 17:00".
    start_time TIME,
    end_time TIME,
    title VARCHAR(150) NOT NULL,
    description VARCHAR(2000),
    -- Only tasks carry it, and as a NUMBER: "max 2200 kcal" typed into a title cannot be counted.
    target_calories INTEGER,
    -- Who authored / last touched the row. Drives the unread dots in both directions: the client is
    -- alerted only about the coach's changes and vice versa.
    created_by_admin BOOLEAN NOT NULL,
    last_modified_by_admin BOOLEAN NOT NULL,
    completed_at TIMESTAMPTZ,
    feedback VARCHAR(2000),
    rpe SMALLINT,
    -- Optimistic locking from day one: coach and client can be editing the same row at once.
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_personal_trainings_kind CHECK (kind IN ('TRAINING', 'TASK')),
    -- An end without a start is meaningless, and without an hour grid only the database can say so.
    CONSTRAINT chk_personal_trainings_times
        CHECK (end_time IS NULL OR (start_time IS NOT NULL AND end_time > start_time)),
    CONSTRAINT chk_personal_trainings_rpe CHECK (rpe IS NULL OR rpe BETWEEN 1 AND 10),
    -- RPE only exists as part of ticking a training off, so uncompleting must clear it.
    CONSTRAINT chk_personal_trainings_rpe_needs_completion CHECK (completed_at IS NOT NULL OR rpe IS NULL),
    -- "How hard was staying under 2200 kcal, 1-10" is a question about nothing, and an answer would
    -- poison the RPE averages and the overtraining signal.
    CONSTRAINT chk_personal_trainings_rpe_is_training CHECK (rpe IS NULL OR kind = 'TRAINING'),
    -- 500 is below any sane daily limit and 10000 above any sane one: catches a slipped digit.
    CONSTRAINT chk_personal_trainings_calories
        CHECK (target_calories IS NULL OR (kind = 'TASK' AND target_calories BETWEEN 500 AND 10000))
);

-- Every calendar read is "this client, this date range".
CREATE INDEX idx_personal_trainings_athlete_date ON personal_trainings (athlete_id, training_date);
-- Unread counters scan by client + recency; this runs on every window focus.
CREATE INDEX idx_personal_trainings_unread ON personal_trainings (athlete_id, updated_at DESC);

-- Conversation attached to a single training. Two people ever, so no thread model is needed.
CREATE TABLE training_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    training_id UUID NOT NULL REFERENCES personal_trainings(id) ON DELETE CASCADE,
    -- The author may later delete their account; the comment stays as context for the other side.
    author_id UUID REFERENCES users(id) ON DELETE SET NULL,
    -- Role FROZEN at the time of writing. Deriving it from users.role later would relabel a client's
    -- old comments as coach comments the day they are made an admin, and flip their unread dots.
    author_is_admin BOOLEAN NOT NULL,
    body VARCHAR(1000),
    -- The photo is a COLUMN on the comment, not a row in training_attachments: the unread service
    -- keeps working unchanged, and a client's screenshot must not travel with a copied plan. One
    -- photo per comment, at most three per training (enforced in TrainingPhotoService).
    photo_filename VARCHAR(64),
    photo_width SMALLINT,
    photo_height SMALLINT,
    -- Photos are deleted 30 days after upload. STORED rather than derived, so the client sees the
    -- real date and a later change of the retention window does not rewrite existing rows.
    photo_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Text, a photo, or both — never an empty bubble.
    CONSTRAINT chk_tc_content CHECK (body IS NOT NULL OR photo_filename IS NOT NULL)
);

CREATE INDEX idx_training_comments_training ON training_comments (training_id, created_at);
-- Unread counters filter by author role and recency.
CREATE INDEX idx_training_comments_unread ON training_comments (training_id, author_is_admin, created_at DESC);
-- Account erasure unlinks the photos its owner left in somebody else's thread by this column.
CREATE INDEX idx_training_comments_author ON training_comments (author_id);
CREATE INDEX idx_tc_photo_expiry ON training_comments (photo_expires_at) WHERE photo_filename IS NOT NULL;

-- "Last time this viewer looked at this calendar". The client is the row where user_id = athlete_id;
-- a coach gets one row per client, so several admins keep independent counters. A missing row
-- means EPOCH — count everything.
CREATE TABLE training_calendar_reads (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    athlete_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    seen_at TIMESTAMPTZ NOT NULL,
    -- How far into the calendar this viewer has actually looked. A timestamp alone let opening one
    -- page stamp "seen" over the whole plan, swallowing the coach's work in months never opened.
    -- NULL means no day has been reached yet.
    seen_through DATE,
    PRIMARY KEY (user_id, athlete_id)
);

-- Second column of the primary key, which a lookup by client alone cannot use; erasing one person's
-- plan deletes every reader's marker for them.
CREATE INDEX idx_training_calendar_reads_athlete ON training_calendar_reads (athlete_id);

-- A deleted future training leaves nothing behind to point at, so the alert needs its own snapshot.
-- Only FUTURE deletions are recorded: clearing out past entries is housekeeping, not news.
CREATE TABLE training_deletions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    training_date DATE NOT NULL,
    start_time TIME,
    title VARCHAR(150) NOT NULL,
    -- Both sides may delete, and the alert has to travel the other way.
    deleted_by_admin BOOLEAN NOT NULL,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Dismissing the banner is a separate act from "I opened my calendar".
    dismissed_at TIMESTAMPTZ
);

CREATE INDEX idx_training_deletions_athlete ON training_deletions (athlete_id, deleted_at DESC);

-- Goals the coach sets for a client, on three horizons.
CREATE TABLE athlete_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    horizon VARCHAR(10) NOT NULL,
    content VARCHAR(500) NOT NULL,
    target_date DATE,
    -- DATE, not a timestamp: achievement is back-dated ("you actually hit this last Tuesday"), and a
    -- time of day would be invented precision.
    achieved_at DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_athlete_goals_horizon CHECK (horizon IN ('SHORT', 'MEDIUM', 'LONG'))
);

-- One ACTIVE goal per horizon, but any number of achieved ones — that pile is the trophy case. A
-- plain unique constraint would cap the client at three goals for life.
CREATE UNIQUE INDEX uq_athlete_goals_active ON athlete_goals (athlete_id, horizon) WHERE achieved_at IS NULL;
CREATE INDEX idx_athlete_goals_athlete ON athlete_goals (athlete_id, achieved_at);

-- ---------------------------------------------------------------------------------------------
-- The coach's materials
-- ---------------------------------------------------------------------------------------------

-- Library of exercise videos (unlisted YouTube links) the coach reuses across clients.
CREATE TABLE exercise_videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    url VARCHAR(500) NOT NULL,
    -- The YouTube id extracted from the URL. Deduplication keys on THIS, not on the URL:
    -- watch?v=X, youtu.be/X and youtu.be/X?t=30 are the same video.
    video_key VARCHAR(64) NOT NULL,
    description VARCHAR(1000),
    -- Lowercased, Polish diacritics stripped, maintained by the entity: "cwiczenie" finds
    -- "Ćwiczenie…". A plain LIKE is microseconds at a few hundred rows; past roughly 5000 videos add
    -- pg_trgm and a GIN index on this column.
    search_text TEXT NOT NULL,
    -- Retiring a video hides it from the picker without breaking the trainings that reference it.
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_exercise_videos_key UNIQUE (video_key)
);

CREATE INDEX idx_exercise_videos_browse ON exercise_videos (archived_at, name);

-- Reusable training skeletons ("Siła A", "Interwały 6x400").
CREATE TABLE training_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(150) NOT NULL,
    description VARCHAR(2000),
    -- Nullable: an untimed training has no duration, so forcing one would generate data nobody uses.
    default_duration_minutes INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_training_templates_duration
        CHECK (default_duration_minutes IS NULL OR default_duration_minutes BETWEEN 15 AND 720)
);

-- Material attached to a training or to a template — never both.
CREATE TABLE training_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    training_id UUID REFERENCES personal_trainings(id) ON DELETE CASCADE,
    template_id UUID REFERENCES training_templates(id) ON DELETE CASCADE,
    -- FILE joins this list when uploads land; extending the CHECK is then the whole migration.
    kind VARCHAR(10) NOT NULL,
    label VARCHAR(150),
    url VARCHAR(500),
    -- RESTRICT, not CASCADE: reference counting done by the database. A video in use cannot be
    -- deleted (the coach archives it instead), so no counter has to be kept in sync by hand.
    video_id UUID REFERENCES exercise_videos(id) ON DELETE RESTRICT,
    position SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ta_owner CHECK (num_nonnulls(training_id, template_id) = 1),
    CONSTRAINT chk_ta_kind CHECK (
        (kind = 'LINK' AND url IS NOT NULL AND video_id IS NULL)
        OR (kind = 'VIDEO' AND video_id IS NOT NULL AND url IS NULL)),
    CONSTRAINT chk_ta_position CHECK (position BETWEEN 0 AND 2)
);

-- Closes the "max 3 materials" rule in the database; enforcing it only in the service would let two
-- concurrent writes slip past it.
CREATE UNIQUE INDEX uq_ta_training_pos ON training_attachments (training_id, position) WHERE training_id IS NOT NULL;
CREATE UNIQUE INDEX uq_ta_template_pos ON training_attachments (template_id, position) WHERE template_id IS NOT NULL;
CREATE INDEX idx_ta_video ON training_attachments (video_id) WHERE video_id IS NOT NULL;

-- The coach's private notebook: one note per author per training, readable by nobody else (the
-- isolation is enforced in code — see AdminNoteIsolationArchTest).
CREATE TABLE admin_private_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    training_id UUID NOT NULL REFERENCES personal_trainings(id) ON DELETE CASCADE,
    body VARCHAR(4000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_apn_body_not_blank CHECK (btrim(body) <> ''),
    -- A second write by the same author is a correction, settled by ON CONFLICT on this constraint.
    CONSTRAINT uq_apn_training UNIQUE (author_id, training_id)
);

CREATE INDEX idx_apn_training ON admin_private_notes (training_id);
