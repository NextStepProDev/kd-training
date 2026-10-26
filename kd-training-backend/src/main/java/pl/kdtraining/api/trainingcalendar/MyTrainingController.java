package pl.kdtraining.api.trainingcalendar;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.kdtraining.api.trainingcalendar.TrainingCalendarDtos.*;
import pl.kdtraining.config.CurrentUserId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Client side of the 1-on-1 calendar. Authenticated by the {@code /api/user/**} prefix; whether the
 * caller is actually a coaching client is settled by {@link TrainingAccessService}, which answers 404
 * (not 403) so an ordinary account cannot tell the feature exists.
 * <p>
 * The path segment {@code my-training} also selects a dedicated rate-limit bucket — see
 * {@code RateLimitFilter}.
 */
@RestController
@RequestMapping("/api/user/my-training")
public class MyTrainingController {

    private final PersonalTrainingService service;
    private final AthleteGoalService goalService;
    private final TrainingStatsService statsService;

    public MyTrainingController(PersonalTrainingService service,
                                AthleteGoalService goalService,
                                TrainingStatsService statsService) {
        this.service = service;
        this.goalService = goalService;
        this.statsService = statsService;
    }

    @GetMapping("/calendar")
    public CalendarRangeResponse getRange(
            @CurrentUserId UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.getRange(userId, from, to, userId, false);
    }

    /** Cheap payload behind the account tile badge — no calendar page, just the counters. */
    @GetMapping("/summary")
    public MyTrainingSummary summary(@CurrentUserId UUID userId) {
        return service.summary(userId, userId, false);
    }

    @PostMapping("/trainings")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalTrainingResponse create(@CurrentUserId UUID userId,
                                           @Valid @RequestBody CreateTrainingRequest request) {
        return service.create(userId, request, false);
    }

    @PutMapping("/trainings/{id}")
    public PersonalTrainingResponse update(@CurrentUserId UUID userId, @PathVariable UUID id,
                                           @Valid @RequestBody UpdateTrainingRequest request) {
        return service.update(id, request, userId, false);
    }

    @DeleteMapping("/trainings/{id}")
    public ResponseEntity<Void> delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        service.delete(id, userId, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/trainings/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalTrainingResponse duplicate(@CurrentUserId UUID userId, @PathVariable UUID id,
                                              @Valid @RequestBody DuplicateTrainingRequest request) {
        return service.duplicate(id, request, userId, false);
    }

    @PostMapping("/trainings/paste")
    public PersonalTrainingResponse paste(@CurrentUserId UUID userId,
                                          @Valid @RequestBody PasteTrainingRequest request) {
        return service.paste(request, userId, false);
    }

    /** Ticking off is the client's act alone — there is deliberately no coach equivalent. */
    @PostMapping("/trainings/{id}/complete")
    public PersonalTrainingResponse complete(@CurrentUserId UUID userId, @PathVariable UUID id,
                                             @Valid @RequestBody CompleteTrainingRequest request) {
        return service.complete(id, request, userId);
    }

    @DeleteMapping("/trainings/{id}/complete")
    public PersonalTrainingResponse uncomplete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return service.uncomplete(id, userId);
    }

    @GetMapping("/trainings/{id}/comments")
    public List<TrainingCommentResponse> comments(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return service.getComments(id, userId, false);
    }

    @PostMapping("/trainings/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingCommentResponse addComment(@CurrentUserId UUID userId, @PathVariable UUID id,
                                              @Valid @RequestBody AddCommentRequest request) {
        return service.addComment(id, request, userId, false);
    }

    /**
     * Clears the dots for the window that was on screen — not for the whole plan.
     * <p>
     * {@code to} is required, because without it the call has no meaning left: "I looked" is exactly
     * the claim that used to wipe a month nobody opened. A cached bundle mid-deploy gets a 400, so
     * its dots stay lit for that visit and the badge keeps the value it had — the safe direction,
     * and self-correcting on the next load. Accepting the call and silently claiming everything
     * would lose the notice for good.
     */
    @PostMapping("/mark-seen")
    public ResponseEntity<Void> markSeen(
            @CurrentUserId UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        service.markSeen(userId, userId, false, to);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/deletions/dismiss")
    public ResponseEntity<Void> dismissDeletions(@CurrentUserId UUID userId) {
        service.dismissDeletions(userId, false);
        return ResponseEntity.noContent().build();
    }

    /** Same numbers as the coach sees, minus the overtraining signal. */
    @GetMapping("/stats")
    public TrainingStatsResponse stats(@CurrentUserId UUID userId) {
        return statsService.stats(userId, false);
    }

    /** Read-only: goals are the coach's call, the trophy case is the client's to look at. */
    @GetMapping("/goals")
    public GoalsResponse goals(@CurrentUserId UUID userId) {
        return goalService.getGoals(userId, userId, false);
    }
}
