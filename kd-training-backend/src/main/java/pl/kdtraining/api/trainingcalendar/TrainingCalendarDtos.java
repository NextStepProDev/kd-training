package pl.kdtraining.api.trainingcalendar;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.kdtraining.domain.training.AttachmentKind;
import pl.kdtraining.domain.training.GoalHorizon;
import pl.kdtraining.domain.training.TrainingKind;
import pl.kdtraining.domain.training.TrainingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared response shapes for the 1-on-1 training calendar.
 * <p>
 * Both the coach view ({@code /api/admin/...}) and the client view ({@code /api/user/my-training/...})
 * return the SAME records — that is the whole point of keeping the two controllers in one package.
 * The frontend renders one component for both roles, so any divergence here would immediately show up
 * as a second code path on the client.
 */
public final class TrainingCalendarDtos {

    private TrainingCalendarDtos() {}

    /** One row of the coach's roster. The overtraining flag lands here later. */
    public record AthleteSummary(
            UUID id,
            String firstName,
            String lastName,
            String email,
            @Nullable String avatarUrl,
            long unreadCount
    ) {}

    /**
     * A single entry on the plan — a training or a task.
     *
     * @param kind           TRAINING or TASK. Fixed at creation; the same shape serves both so the
     *                       calendar renders one list, not two merged ones
     * @param targetCalories tasks only, and optional there too
     * @param status         computed server-side and never stored; the frontend only colours it, so
     *                       the two can never disagree about what "missed" means at a day boundary
     * @param version        echoed back on update — the client holds it to detect a concurrent edit
     */
    public record PersonalTrainingResponse(
            UUID id,
            TrainingKind kind,
            LocalDate date,
            @Nullable LocalTime startTime,
            @Nullable LocalTime endTime,
            String title,
            @Nullable String description,
            @Nullable Integer targetCalories,
            TrainingStatus status,
            @Nullable Instant completedAt,
            @Nullable String feedback,
            @Nullable Integer rpe,
            boolean createdByAdmin,
            boolean lastModifiedByAdmin,
            /** The other side touched this since the viewer last looked — drives the dot on the card. */
            boolean unread,
            int commentCount,
            List<AttachmentResponse> attachments,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /** One calendar page of the 1-on-1 plan. */
    public record CalendarRangeResponse(
            LocalDate from,
            LocalDate to,
            List<PersonalTrainingResponse> trainings,
            /** Deleted future trainings the viewer has not dismissed yet. */
            List<DeletedTrainingNotice> deletions
    ) {}

    public record DeletedTrainingNotice(
            UUID id,
            LocalDate date,
            @Nullable LocalTime startTime,
            String title,
            Instant deletedAt
    ) {}

    public record TrainingCommentResponse(
            UUID id,
            /** Null when the comment is a bare photo. */
            @Nullable String body,
            boolean fromCoach,
            @Nullable String authorName,
            Instant createdAt,
            @Nullable TrainingCommentPhoto photo
    ) {}

    /**
     * A photo attached to a comment.
     *
     * @param url       role-aware endpoint, built the way UserService builds avatar URLs. It needs a
     *                  bearer token — this is health data and never reaches the public file namespace
     * @param width     dimensions of the stored file, so the client can reserve the box and keep the
     *                  thread from jumping while the bytes are still in flight
     * @param expiresAt when the retention sweep will delete it, shown so nobody is surprised by it
     * @param canDelete whether THIS viewer may remove it: its author always, and the coach for
     *                  anything in their client's thread
     */
    public record TrainingCommentPhoto(
            String url,
            int width,
            int height,
            Instant expiresAt,
            boolean canDelete
    ) {}

    public record AddCommentRequest(
            @NotBlank @Size(max = 1000) String body
    ) {}

    /** Lightweight payload behind the account tile badge — deliberately cheaper than a full page. */
    public record MyTrainingSummary(
            long unreadCount,
            int deletedCount,
            @Nullable LocalDate nextTrainingDate
    ) {}

    /**
     * Times are optional and omitting them is the normal case. Passing an end without a start is
     * rejected — without an hour grid there is nothing for a bare end time to mean.
     *
     * @param attachments {@code null} = leave materials alone · {@code []} = clear them ·
     *                    a list = replace with exactly that. The three-way contract matters because
     *                    a drag-and-drop re-date sends the whole training and must not silently drop
     *                    the materials it never touched.
     */
    public record CreateTrainingRequest(
            /** Omitted means TRAINING — the overwhelmingly common entry, and what older clients send. */
            @Nullable TrainingKind kind,
            @NotNull LocalDate date,
            @Nullable LocalTime startTime,
            @Nullable LocalTime endTime,
            @NotBlank @Size(max = 150) String title,
            @Nullable @Size(max = 2000) String description,
            /** Ignored on a training: only a task has a calorie ceiling. */
            @Nullable @Min(500) @Max(10000) Integer targetCalories,
            @Nullable @Size(max = 3) List<@Valid AttachmentRequest> attachments
    ) {}

    /** No {@code kind}: an entry is a training or a task from birth, and stays what it was. */
    public record UpdateTrainingRequest(
            @NotNull LocalDate date,
            @Nullable LocalTime startTime,
            @Nullable LocalTime endTime,
            @NotBlank @Size(max = 150) String title,
            @Nullable @Size(max = 2000) String description,
            @Nullable @Min(500) @Max(10000) Integer targetCalories,
            @Nullable @Size(max = 3) List<@Valid AttachmentRequest> attachments,
            @NotNull Long version
    ) {}

    /** Exactly one of {@code url} / {@code videoId} must be set, matching {@code kind}. */
    public record AttachmentRequest(
            @NotNull AttachmentKind kind,
            @Nullable @Size(max = 150) String label,
            @Nullable @Size(max = 500) String url,
            @Nullable UUID videoId
    ) {}

    public record AttachmentResponse(
            UUID id,
            AttachmentKind kind,
            @Nullable String label,
            /** For a LINK the address itself; for a VIDEO the library entry's own URL. */
            @Nullable String url,
            @Nullable UUID videoId,
            @Nullable String videoName,
            /** Canonical player URL, built from the id — a pasted link never reaches an iframe. */
            @Nullable String embedUrl,
            @Nullable String thumbnailUrl
    ) {}

    public record ExerciseVideoResponse(
            UUID id,
            String name,
            String url,
            @Nullable String description,
            String embedUrl,
            String thumbnailUrl,
            boolean archived
    ) {}

    public record PagedExerciseVideos(
            List<ExerciseVideoResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    /**
     * What a pasted link turns out to be, before anything is saved.
     *
     * @param status        OK · UNAVAILABLE (private, deleted, embedding off) · UNKNOWN (YouTube did
     *                      not answer — never a reason to block the save)
     * @param duplicateName name of the clip already holding this video id, or null. Matching is on
     *                      the id, so youtu.be/X and watch?v=X are the same film
     */
    public record VideoMetadataResponse(
            String status,
            @Nullable String title,
            @Nullable String authorName,
            String thumbnailUrl,
            @Nullable String duplicateName
    ) {}

    public record ExerciseVideoRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 500) String url,
            @Nullable @Size(max = 1000) String description
    ) {}

    public record TrainingTemplateResponse(
            UUID id,
            String title,
            @Nullable String description,
            @Nullable Integer defaultDurationMinutes,
            List<AttachmentResponse> attachments
    ) {}

    /**
     * @param totalCount          every 1-on-1 training ever ticked off — counted in the database, not
     *                            from the rolling year the rest of this response is built on
     * @param firstActivityDate   the earliest one, on the same lifetime basis, so the date a client
     *                            started does not drift forward as old sessions leave the window
     * @param heatmap             non-zero days only, so a year of squares stays a small payload
     * @param attendancePercent   null when nothing was planned in the window — 0% would claim a
     *                            failure that never happened
     * @param overtraining        null for the client: this signal is for the coach's eyes only, and
     *                            the field is absent from their JSON entirely rather than false
     */
    public record TrainingStatsResponse(
            int thisMonthCount,
            int prevMonthCount,
            int totalCount,
            @Nullable LocalDate firstActivityDate,
            int currentStreakWeeks,
            int bestStreakWeeks,
            @Nullable Double avgPerMonth,
            Map<LocalDate, Integer> heatmap,
            @Nullable Integer attendancePercent,
            @Nullable Double avgRpeOverall,
            @Nullable Double avgRpeRecent,
            Map<String, Integer> rpeDistribution,
            TaskBreakdown tasks,
            @Nullable Boolean overtraining
    ) {}

    /**
     * Tasks, counted apart from everything above.
     * <p>
     * Every training number in this response — streak, attendance, heatmap, monthly counts — ignores
     * tasks entirely. Holding a calorie ceiling is not a session, and letting it feed the streak would
     * turn "8 tygodni z rzędu" into a sentence about nothing in particular.
     * <p>
     * Every count ships with the denominator it belongs to. "3 done this month" is unreadable on its
     * own: it could be three out of three or three out of twelve, and a bare 0 cannot tell a month
     * of blown ceilings from a month where none were set. Two counts, two windows, and each pair
     * says something without borrowing meaning from the other.
     *
     * @param thisMonthDue      tasks in the current month that have come due — a task still ahead of
     *                          the client is not something they have failed to hold
     * @param windowDue         same, over the 90 days attendance uses
     * @param completionPercent {@code windowDone / windowDue}; null when nothing came due in the
     *                          window, since 0% would report a failure nobody had
     */
    public record TaskBreakdown(
            int thisMonthDone,
            int thisMonthDue,
            int windowDone,
            int windowDue,
            @Nullable Integer completionPercent
    ) {}

    /**
     * @param unread                null for the coach — a goal is theirs to write, so it is never
     *                              news to them, and the field is absent from their JSON rather than
     *                              false. Same shape as {@code overtraining}. Without it the client's
     *                              badge counts a new goal that nothing on the page points at.
     */
    public record GoalResponse(
            UUID id,
            GoalHorizon horizon,
            String content,
            @Nullable LocalDate targetDate,
            @Nullable LocalDate achievedAt,
            @Nullable Boolean unread
    ) {}

    /** Active goals render as three cards; achieved ones pile up in the trophy case. */
    public record GoalsResponse(
            List<GoalResponse> active,
            List<GoalResponse> achieved
    ) {}

    public record GoalRequest(
            @NotNull GoalHorizon horizon,
            @NotBlank @Size(max = 500) String content,
            @Nullable LocalDate targetDate
    ) {}

    /** Back-datable: the coach usually notices a goal was reached some days later. */
    public record AchieveGoalRequest(
            @Nullable LocalDate achievedDate
    ) {}

    public record TrainingTemplateRequest(
            @NotBlank @Size(max = 150) String title,
            @Nullable @Size(max = 2000) String description,
            @Nullable @Min(15) @Max(720) Integer defaultDurationMinutes,
            @Nullable @Size(max = 3) List<@Valid AttachmentRequest> attachments
    ) {}

    /**
     * @param rpe mandatory on a training — a ticked-off session with no perceived effort tells the
     *            coach nothing — and rejected on a task, where the question means nothing. Which of
     *            the two applies is decided by the entry, so the check cannot live in an annotation.
     */
    public record CompleteTrainingRequest(
            @Nullable @Min(1) @Max(10) Integer rpe,
            @Nullable @Size(max = 2000) String feedback
    ) {}

    /**
     * Shifts a copy forward; the default lands it on the same weekday next week.
     *
     * @param offsetDays bounded to a year either way. Not a policy — arithmetic: the offset is fed
     *                   to {@code LocalDate.plusDays}, which throws on a value that runs off the
     *                   end of the calendar, and an unbounded field turns a typed digit into a 500.
     *                   A year is far past anything anyone would plan by duplicating one session.
     */
    public record DuplicateTrainingRequest(
            @Nullable @Min(-365) @Max(365) Integer offsetDays
    ) {}

    public enum PasteMode {
        /** Leaves the source alone. */
        COPY,
        /** Moves the original, so its id, completion and (later) comments survive the move. */
        MOVE
    }

    /**
     * @param targetAthleteId whose calendar the entry lands in. Null means the source's own athlete,
     *                        which is what an older client sends and what a client-side paste always
     *                        means. The coach sends the calendar currently on screen — without it the
     *                        server can only guess, and the guess was "the source's athlete", so a
     *                        paste made after switching clients silently landed on the previous one.
     *                        Only the coach may name someone else; a client naming anyone but
     *                        themselves gets the same 404 as a stranger's calendar.
     */
    public record PasteTrainingRequest(
            @NotNull UUID sourceId,
            @NotNull LocalDate targetDate,
            @NotNull PasteMode mode,
            @Nullable UUID targetAthleteId
    ) {}
}
