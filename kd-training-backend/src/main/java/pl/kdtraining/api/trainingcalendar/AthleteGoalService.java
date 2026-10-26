package pl.kdtraining.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.api.NotFoundException;
import pl.kdtraining.api.trainingcalendar.TrainingCalendarDtos.*;
import pl.kdtraining.domain.training.AthleteGoal;
import pl.kdtraining.domain.training.AthleteGoalRepository;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.infrastructure.i18n.MessageService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.UUID;

/**
 * Goals on three horizons, set by the coach and read-only for the client.
 * <p>
 * Two rules the database enforces and this service explains:
 * one active goal per horizon (partial unique index), and achieved goals are immutable — they are
 * the trophy case, and editing one would rewrite somebody's history.
 */
@Service
public class AthleteGoalService {

    private final AthleteGoalRepository repository;
    private final TrainingAccessService access;
    private final TrainingUnreadService unread;
    private final MessageService msg;

    public AthleteGoalService(AthleteGoalRepository repository,
                              TrainingAccessService access,
                              TrainingUnreadService unread,
                              MessageService msg) {
        this.repository = repository;
        this.access = access;
        this.unread = unread;
        this.msg = msg;
    }

    /**
     * A new goal is one of the seven things the client's badge counts, and until now it was the only
     * one with nowhere on the page to point at: the number went up and every card looked the same.
     * The mark comes off the SAME read marker as the calendar, because both sit on one screen —
     * a second notion of "seen" would need its own way of being cleared.
     * <p>
     * Goals carry no date, so only the timestamp half of the marker applies; there is no page of the
     * plan a goal can be out of reach on.
     */
    @Transactional(readOnly = true)
    public GoalsResponse getGoals(UUID athleteId, UUID viewerId, boolean viewerIsAdmin) {
        access.requireAthlete(athleteId);
        // Absent for the coach: a goal they wrote is never news to them, and the field stays out of
        // their JSON rather than arriving as false.
        Instant since = viewerIsAdmin ? null : unread.seenMarker(viewerId, athleteId).at();
        return new GoalsResponse(
                repository.findActive(athleteId).stream()
                        // By the enum's own order (SHORT, MEDIUM, LONG) — see the repository for why
                        // this cannot be left to SQL.
                        .sorted(Comparator.comparing(AthleteGoal::getHorizon))
                        .map(g -> toResponse(g, since)).toList(),
                repository.findAchieved(athleteId).stream().map(g -> toResponse(g, since)).toList());
    }

    @Transactional
    public GoalResponse create(UUID athleteId, GoalRequest request) {
        User athlete = access.requireAthlete(athleteId);

        // Pre-check for a readable message, plus a catch below: two coaches saving at once would
        // otherwise surface the raw constraint violation.
        boolean slotTaken = repository.findActive(athleteId).stream()
                .anyMatch(g -> g.getHorizon() == request.horizon());
        if (slotTaken) {
            throw new IllegalStateException(msg.get("athletegoal.duplicate.active"));
        }

        AthleteGoal goal = new AthleteGoal(athlete, request.horizon(), request.content().trim(),
                request.targetDate());
        try {
            return toResponse(repository.saveAndFlush(goal));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(msg.get("athletegoal.duplicate.active"));
        }
    }

    @Transactional
    public GoalResponse update(UUID goalId, GoalRequest request) {
        AthleteGoal goal = requireEditable(goalId);
        // The horizon is not editable: a short-term goal that became long-term is a different goal.
        goal.edit(request.content().trim(), request.targetDate());
        return toResponse(repository.save(goal));
    }

    @Transactional
    public void delete(UUID goalId) {
        repository.delete(requireEditable(goalId));
    }

    /**
     * Marks a goal reached, optionally on an earlier date — the coach usually notices afterwards.
     * A future date is refused: nothing has been achieved yet.
     */
    @Transactional
    public GoalResponse achieve(UUID goalId, AchieveGoalRequest request) {
        AthleteGoal goal = requireEditable(goalId);
        LocalDate date = request.achievedDate() == null ? LocalDate.now() : request.achievedDate();
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(msg.get("athletegoal.achieved.future"));
        }
        goal.achieve(date);
        return toResponse(repository.save(goal));
    }

    /**
     * Resolves a goal that may be reached at all — the counterpart of
     * {@link TrainingAccessService#requireTraining}, and here for the same two reasons.
     * <p>
     * A goal belonging to someone whose client flag has been cleared is out of reach: dropping the
     * flag is what puts a person's plan away, and a goal left editable behind it would be the one
     * piece of their record that never got put away with it. A goal that is not theirs answers the
     * same 404 as one that does not exist, so a mistyped id cannot confirm that some other client's
     * goal is real.
     */
    private AthleteGoal requireGoal(UUID goalId) {
        AthleteGoal goal = repository.findById(goalId)
                .orElseThrow(() -> new NotFoundException(msg.get("athletegoal.not.found")));
        if (!goal.getAthlete().isAthlete()) {
            throw new NotFoundException(msg.get("athletegoal.not.found"));
        }
        return goal;
    }

    private AthleteGoal requireEditable(UUID goalId) {
        AthleteGoal goal = requireGoal(goalId);
        if (goal.isAchieved()) {
            throw new IllegalStateException(msg.get("athletegoal.achieved.immutable"));
        }
        return goal;
    }

    /** Writes echo back their own change, so nothing they just saved is news: {@code unread} absent. */
    static GoalResponse toResponse(AthleteGoal g) {
        return toResponse(g, null);
    }

    /** @param since null for the coach — see {@link #getGoals}. */
    private static GoalResponse toResponse(AthleteGoal g, @Nullable Instant since) {
        return new GoalResponse(g.getId(), g.getHorizon(), g.getContent(),
                g.getTargetDate(), g.getAchievedAt(),
                since == null ? null : g.getCreatedAt().isAfter(since));
    }
}
