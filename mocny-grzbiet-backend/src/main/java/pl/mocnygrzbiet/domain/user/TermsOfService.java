package pl.mocnygrzbiet.domain.user;

/**
 * The version of the terms of service in force. Changing the text of the terms means changing this
 * label in the same commit: everyone who accepted an earlier version is asked again on their next visit
 * (the client's answer, 08.11.2026), and until they do, the site stays behind the completion screen.
 * <p>
 * The label is the date of the change, and it is also the "last updated" date on the terms page
 * (TermsPage.tsx, LAST_UPDATED) — change both together. A wording fix that changes nothing for anybody
 * (a typo) does not need a new version.
 */
public final class TermsOfService {

    public static final String CURRENT_VERSION = "2026-11-08";

    private TermsOfService() {}
}
