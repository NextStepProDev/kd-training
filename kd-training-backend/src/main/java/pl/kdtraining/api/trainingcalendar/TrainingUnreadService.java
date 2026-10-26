package pl.kdtraining.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.domain.training.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything that can be "new" on a training calendar, in one place.
 * <p>
 * The complete list was settled before the first counter was written, because the failure mode is
 * silent: a source nobody remembered simply never lights a dot, and the gap only surfaces when
 * someone complains that a change went unnoticed.
 *
 * <p><b>News for the client (from the coach):</b>
 * <ol>
 *   <li>a training the coach created or edited</li>
 *   <li>a comment written by the coach</li>
 *   <li>a future training the coach deleted</li>
 *   <li>a goal the coach set — the one source with no counterpart below</li>
 * </ol>
 *
 * <p><b>News for the coach (from the client):</b>
 * <ol start="5">
 *   <li>a training the client created or edited — this covers ticking off AND undoing it</li>
 *   <li>a comment written by the client</li>
 *   <li>a future training the client deleted</li>
 * </ol>
 *
 * <p>Symmetry is the point for the six that pair up: both directions are computed by the same code
 * with the role flag flipped, so a rule cannot be enforced one way and forgotten the other. Goals
 * are the deliberate exception, and the code says so at the point where it breaks the symmetry.
 *
 * <p><b>Being caught up has two edges.</b> "As of when" is not enough on its own: the calendar is
 * read one page at a time, so opening this week said nothing about next month — yet it used to stamp
 * the whole plan as seen. The marker therefore also carries how far the viewer got, and a source is
 * unread when the other side touched it after the last visit OR when it sits past that day.
 * Deletions are the exception: they arrive through the banner, which is not bounded by the page.
 */
@Service
public class TrainingUnreadService {

    private final PersonalTrainingRepository trainingRepository;
    private final TrainingCommentRepository commentRepository;
    private final TrainingDeletionRepository deletionRepository;
    private final TrainingCalendarReadRepository readRepository;
    private final AthleteGoalRepository goalRepository;

    public TrainingUnreadService(PersonalTrainingRepository trainingRepository,
                                 TrainingCommentRepository commentRepository,
                                 TrainingDeletionRepository deletionRepository,
                                 TrainingCalendarReadRepository readRepository,
                                 AthleteGoalRepository goalRepository) {
        this.trainingRepository = trainingRepository;
        this.commentRepository = commentRepository;
        this.deletionRepository = deletionRepository;
        this.readRepository = readRepository;
        this.goalRepository = goalRepository;
    }

    /**
     * How caught up this viewer is with this calendar.
     *
     * @param at      nothing changed before this instant is news
     * @param through the last calendar day they actually opened; null means none yet, so everything
     *                on the plan is still unseen however old it is
     */
    public record SeenMarker(Instant at, @Nullable LocalDate through) {
        /**
         * Stands in for "no day reached yet" wherever the reach is compared.
         * <p>
         * A sentinel rather than a null check in the query: {@code :param IS NULL} gives Postgres no
         * type to infer the bind from, and the statement fails outright. Every real training date is
         * after this one, so an unopened calendar correctly counts as entirely unseen.
         */
        private static final LocalDate NOTHING_REACHED = LocalDate.of(1, 1, 1);

        /** A calendar nobody has opened: everything counts as new. */
        static final SeenMarker NEVER = new SeenMarker(Instant.EPOCH, null);

        /** The reach, never null — pass this to anything that compares dates. */
        public LocalDate reach() {
            return through == null ? NOTHING_REACHED : through;
        }
    }

    @Transactional(readOnly = true)
    public SeenMarker seenMarker(UUID viewerId, UUID athleteId) {
        return readRepository.findByUserIdAndAthleteId(viewerId, athleteId)
                .map(read -> new SeenMarker(read.getSeenAt(), read.getSeenThrough()))
                .orElse(SeenMarker.NEVER);
    }

    /**
     * How many things the other side has done since this viewer last looked.
     *
     * @param viewerIsAdmin the coach counts the client's activity, and vice versa
     */
    @Transactional(readOnly = true)
    public long countUnread(UUID viewerId, UUID athleteId, boolean viewerIsAdmin) {
        SeenMarker seen = seenMarker(viewerId, athleteId);
        boolean fromAdmin = !viewerIsAdmin;
        long count = trainingRepository.countTouchedSince(athleteId, fromAdmin, seen.at(), seen.reach())
                + commentRepository.countSince(athleteId, fromAdmin, seen.at(), seen.reach())
                // Deletions carry no page of their own — the banner shows them whatever window is
                // open — so they are counted on time alone.
                + deletionRepository.countSince(athleteId, fromAdmin, seen.at());
        // Goals are the coach's alone, so a new one is news for the client and never the other way.
        if (!viewerIsAdmin) {
            count += goalRepository.countCreatedSince(athleteId, seen.at());
        }
        return count;
    }

    /**
     * Unread counts for a whole roster in three queries, whatever its size.
     * <p>
     * Calling {@link #countUnread} per athlete would cost 1 + 4N — 61 queries on a roster of
     * fifteen. Each athlete has their own seen marker, so the batching happens through a LEFT JOIN
     * on it rather than a plain IN-count.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> countUnreadForRoster(UUID viewerId, Collection<UUID> athleteIds,
                                                boolean viewerIsAdmin) {
        Map<UUID, Long> totals = new HashMap<>();
        if (athleteIds.isEmpty()) {
            return totals;
        }
        boolean fromAdmin = !viewerIsAdmin;
        for (var row : readRepository.countUnreadTrainings(viewerId, athleteIds, fromAdmin)) {
            totals.merge(row.getAthleteId(), row.getTotal(), Long::sum);
        }
        for (var row : readRepository.countUnreadComments(viewerId, athleteIds, fromAdmin)) {
            totals.merge(row.getAthleteId(), row.getTotal(), Long::sum);
        }
        for (var row : readRepository.countUnreadDeletions(viewerId, athleteIds, fromAdmin)) {
            totals.merge(row.getAthleteId(), row.getTotal(), Long::sum);
        }
        return totals;
    }

    /** Per-training dots for one calendar page, batched — one query for the whole range. */
    @Transactional(readOnly = true)
    public Set<UUID> unreadTrainingIds(List<UUID> trainingIds, boolean viewerIsAdmin, SeenMarker seen) {
        if (trainingIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(commentRepository.findTrainingIdsWithNewComments(
                trainingIds, !viewerIsAdmin, seen.at(), seen.reach()));
    }

    /**
     * Stamps "seen" for this viewer, up to the last day of the page they were looking at.
     * <p>
     * The caller decides WHEN: the frontend must wait until the calendar has actually rendered, or
     * the dots are cleared before anyone sees them. It also decides WHAT — {@code viewedThrough} is
     * the end of the window on screen, and claiming more than that is how a month nobody opened got
     * marked as read.
     */
    @Transactional
    public void markSeen(UUID viewerId, UUID athleteId, LocalDate viewedThrough) {
        readRepository.upsertSeen(viewerId, athleteId, Instant.now(), viewedThrough);
    }
}
