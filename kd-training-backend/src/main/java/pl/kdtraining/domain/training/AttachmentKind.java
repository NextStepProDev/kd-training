package pl.kdtraining.domain.training;

/**
 * What a piece of material actually is.
 * <p>
 * {@code FILE} joins this list when uploads land — the storage and authorization work that needs is
 * deliberately kept out of the first release.
 */
public enum AttachmentKind {
    /** A one-off address belonging to this training alone. */
    LINK,
    /** A reference into the exercise-video library, shared by every training that uses it. */
    VIDEO
}
