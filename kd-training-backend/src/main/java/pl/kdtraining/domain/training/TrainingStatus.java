package pl.kdtraining.domain.training;

/**
 * Lifecycle of a 1-on-1 training. Always computed from the date and {@code completedAt} — never stored.
 * A stored status would need a nightly job to flip PLANNED to MISSED, and would drift the moment one
 * run was missed or a training was back-dated.
 */
public enum TrainingStatus {
    PLANNED,
    COMPLETED,
    /** In the past and never ticked off. */
    MISSED
}
