-- Which version of the terms a person accepted (the client's answer: when the terms change, clients
-- accept them again). The date alone cannot tell: "accepted on 10 March" says nothing about whether the
-- text changed that afternoon. The version is a label from TermsOfService.CURRENT_VERSION in the code.
--
-- Everyone who has accepted so far accepted the draft in force when this shipped, labelled 2026-11-08.
ALTER TABLE users ADD COLUMN terms_version VARCHAR(20);

UPDATE users SET terms_version = '2026-11-08' WHERE terms_accepted_at IS NOT NULL;
