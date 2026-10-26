package pl.kdtraining.domain.training;

/**
 * What an entry on the 1-on-1 plan actually is.
 * <p>
 * The two are separate rows, never one row wearing two hats: doing the session and holding the diet
 * are separate commitments that succeed and fail independently, so each gets its own tick.
 * <p>
 * Fixed when the entry is created and never edited afterwards. Flipping a completed TRAINING into a
 * TASK would have to throw its RPE away to satisfy the database, and losing data as a side effect of
 * a dropdown is not a trade worth making — delete and re-add instead.
 */
public enum TrainingKind {
    /** A session. Ticked off with a perceived-effort rating. */
    TRAINING,
    /** Something to hold to that day — a calorie ceiling, water, steps. Ticked off, never rated. */
    TASK
}
