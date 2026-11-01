package pl.kdtraining.api;

import org.jspecify.annotations.Nullable;

/** Small string helpers shared by the request-handling layer. */
public final class Strings {

    private Strings() {
    }

    /**
     * Trims optional free text, collapsing "the user cleared the field" to {@code null}.
     * <p>
     * Blank and absent mean the same thing for every optional text column in this codebase, and only
     * {@code null} says so to the database — a stored {@code ""} then reads back as "present but empty"
     * and every {@code != null} check downstream is quietly wrong. Four services had grown their own
     * identical copy of this.
     */
    @Nullable
    public static String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Search text made safe to put inside a {@code LIKE '%…%'}: {@code %} and {@code _} are
     * wildcards, so "100%" or "a_b" matched far more than themselves. Backslash is the escape, so
     * it is escaped first. ⚠️ The query must say {@code ESCAPE '\'} itself: Hibernate does not
     * leave Postgres's default backslash escape in force, and without the clause "_" found nothing.
     */
    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
