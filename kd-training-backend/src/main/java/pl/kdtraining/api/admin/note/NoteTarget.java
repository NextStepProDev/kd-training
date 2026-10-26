package pl.kdtraining.api.admin.note;

import java.util.Locale;
import java.util.Optional;

/**
 * What a note is pinned to, as it appears in the URL.
 *
 * <p>Parsed by hand from lowercase rather than bound as {@code @PathVariable NoteTarget}: Spring
 * matches enum constants on their exact name, so binding would force the URL to shout
 * {@code /notes/TRAINING/}. Returning an {@link Optional} keeps the raw segment out of the answer too --
 * the caller writes it, so it does not belong in the error message; that comes from the bundle.
 */
public enum NoteTarget {
    TRAINING;

    public static Optional<NoteTarget> tryFrom(String segment) {
        for (NoteTarget target : values()) {
            if (target.name().toLowerCase(Locale.ROOT).equals(segment)) {
                return Optional.of(target);
            }
        }
        return Optional.empty();
    }
}
