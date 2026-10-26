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

/** Coach side of the 1-on-1 calendar. Role protection comes from the {@code /api/admin/**} prefix. */
@RestController
@RequestMapping("/api/admin/personal-trainings")
public class AdminPersonalTrainingController {

    private final PersonalTrainingService service;
    private final AthleteGoalService goalService;
    private final TrainingStatsService statsService;

    public AdminPersonalTrainingController(PersonalTrainingService service,
                                           AthleteGoalService goalService,
                                           TrainingStatsService statsService) {
        this.service = service;
        this.goalService = goalService;
        this.statsService = statsService;
    }

    @GetMapping
    public CalendarRangeResponse getRange(
            @CurrentUserId UUID adminId,
            @RequestParam UUID athleteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.getRange(athleteId, from, to, adminId, true);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalTrainingResponse create(@RequestParam UUID athleteId,
                                           @Valid @RequestBody CreateTrainingRequest request) {
        return service.create(athleteId, request, true);
    }

    @PutMapping("/{id}")
    public PersonalTrainingResponse update(@CurrentUserId UUID adminId, @PathVariable UUID id,
                                           @Valid @RequestBody UpdateTrainingRequest request) {
        return service.update(id, request, adminId, true);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUserId UUID adminId, @PathVariable UUID id) {
        service.delete(id, adminId, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalTrainingResponse duplicate(@CurrentUserId UUID adminId, @PathVariable UUID id,
                                              @Valid @RequestBody DuplicateTrainingRequest request) {
        return service.duplicate(id, request, adminId, true);
    }

    @PostMapping("/paste")
    public PersonalTrainingResponse paste(@CurrentUserId UUID adminId,
                                          @Valid @RequestBody PasteTrainingRequest request) {
        return service.paste(request, adminId, true);
    }

    @GetMapping("/{id}/comments")
    public List<TrainingCommentResponse> comments(@CurrentUserId UUID adminId, @PathVariable UUID id) {
        return service.getComments(id, adminId, true);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingCommentResponse addComment(@CurrentUserId UUID adminId, @PathVariable UUID id,
                                              @Valid @RequestBody AddCommentRequest request) {
        return service.addComment(id, request, adminId, true);
    }

    /**
     * Clears this coach's dots for this client, for the window that was on screen — not for the whole
     * plan. The frontend calls it only once the calendar has actually rendered; clearing on mount
     * would wipe the dots before anyone saw them.
     * <p>
     * {@code to} is required — see the client-side twin for why "I looked" without a window is the
     * claim that caused this bug in the first place.
     */
    @PostMapping("/mark-seen")
    public ResponseEntity<Void> markSeen(
            @CurrentUserId UUID adminId,
            @RequestParam UUID athleteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        service.markSeen(athleteId, adminId, true, to);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/deletions/dismiss")
    public ResponseEntity<Void> dismissDeletions(@RequestParam UUID athleteId) {
        service.dismissDeletions(athleteId, true);
        return ResponseEntity.noContent().build();
    }

    /** Includes the overtraining signal — it exists to start a conversation, not to be broadcast. */
    @GetMapping("/stats")
    public TrainingStatsResponse stats(@RequestParam UUID athleteId) {
        return statsService.stats(athleteId, true);
    }

    // --- Goals. Set by the coach; the client only reads them. ---

    @GetMapping("/goals")
    public GoalsResponse goals(@CurrentUserId UUID adminId, @RequestParam UUID athleteId) {
        return goalService.getGoals(athleteId, adminId, true);
    }

    @PostMapping("/goals")
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse createGoal(@RequestParam UUID athleteId, @Valid @RequestBody GoalRequest request) {
        return goalService.create(athleteId, request);
    }

    @PutMapping("/goals/{goalId}")
    public GoalResponse updateGoal(@PathVariable UUID goalId, @Valid @RequestBody GoalRequest request) {
        return goalService.update(goalId, request);
    }

    @DeleteMapping("/goals/{goalId}")
    public ResponseEntity<Void> deleteGoal(@PathVariable UUID goalId) {
        goalService.delete(goalId);
        return ResponseEntity.noContent().build();
    }

    /** Back-datable — the coach usually notices a goal was reached some days later. */
    @PostMapping("/goals/{goalId}/achieve")
    public GoalResponse achieveGoal(@PathVariable UUID goalId, @Valid @RequestBody AchieveGoalRequest request) {
        return goalService.achieve(goalId, request);
    }
}
