package pl.kdtraining.domain.training;

import java.util.List;

/**
 * Whether a client's recent sessions look like sustained overreaching.
 * <p>
 * Pure and clock-free, so the threshold can be tested exhaustively rather than inferred from a
 * fixture. The signal is coarse on purpose: it points the coach at a conversation, it does not
 * diagnose anything.
 */
public final class OvertrainingRule {

    /**
     * How many consecutive sessions must be maximal-effort before this counts as a pattern.
     * <p>
     * Six rather than five: a hard training block legitimately runs a week of tough sessions, and a
     * warning that fires on every block is a warning nobody reads.
     */
    public static final int SAMPLE_SIZE = 6;

    /** RPE at or above this is "as hard as I can go". */
    public static final int THRESHOLD = 9;

    private OvertrainingRule() {}

    /**
     * @param recentRpeNewestFirst the client's last RPE ratings, newest first
     * @return true when the most recent {@link #SAMPLE_SIZE} ratings are ALL at or above the
     *         threshold. Fewer ratings than that never trigger it — two hard sessions are not a
     *         trend, and firing early would make the signal meaningless.
     */
    public static boolean isOvertrained(List<Integer> recentRpeNewestFirst) {
        if (recentRpeNewestFirst == null || recentRpeNewestFirst.size() < SAMPLE_SIZE) {
            return false;
        }
        return recentRpeNewestFirst.stream()
                .limit(SAMPLE_SIZE)
                .allMatch(rpe -> rpe != null && rpe >= THRESHOLD);
    }
}
