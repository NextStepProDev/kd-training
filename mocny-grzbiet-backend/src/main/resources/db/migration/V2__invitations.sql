-- Registration is by invitation only: the coach invites a person by e-mail, and the link in that mail
-- is the only way to create an account.
--
-- One open invitation per address (the unique index): inviting again replaces it, so a resent mail
-- kills the old link. A row is deleted when it is used, and by the nightly token cleanup once expired.
--
-- No foreign key to users, on purpose: the invitee has no account yet, and the sender is always the
-- coach. Without one the table also stays out of the GDPR export, which is right: it holds only an
-- address the person was invited at, for at most the invitation's lifetime.
CREATE TABLE invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_invitations_email ON invitations (lower(email));
