package pl.kdtraining.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.api.NotFoundException;
import pl.kdtraining.api.Strings;
import pl.kdtraining.api.trainingcalendar.TrainingCalendarDtos.*;
import pl.kdtraining.domain.training.*;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRepository;
import pl.kdtraining.infrastructure.i18n.MessageService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core of the 1-on-1 calendar, shared by the coach and the client.
 * <p>
 * Both roles get the same operations; the only differences are which athlete they may reach (settled
 * by {@link TrainingAccessService}) and the {@code byAdmin} flag stamped on every write, which is what
 * drives the unread dots. Keeping one implementation means a rule cannot be enforced for one role and
 * forgotten for the other.
 */
@Service
public class PersonalTrainingService {

    /**
     * Widest calendar page we will assemble. A month grid is 42 days and a week is 7, so this leaves
     * headroom while keeping the cost of one page bounded and predictable.
     */
    static final int MAX_RANGE_DAYS = 62;

    private static final int DEFAULT_DUPLICATE_OFFSET_DAYS = 7;

    /** The banner is an alert feed, not an archive — showing every deletion ever would bury the news. */
    private static final int MAX_DELETION_NOTICES = 10;

    private final PersonalTrainingRepository repository;
    private final TrainingCommentRepository commentRepository;
    private final TrainingDeletionRepository deletionRepository;
    private final TrainingAccessService access;
    private final TrainingUnreadService unread;
    private final AttachmentService attachments;
    private final TrainingPhotoService photos;
    private final UserRepository userRepository;
    private final MessageService msg;

    public PersonalTrainingService(PersonalTrainingRepository repository,
                                   TrainingCommentRepository commentRepository,
                                   TrainingDeletionRepository deletionRepository,
                                   TrainingAccessService access,
                                   TrainingUnreadService unread,
                                   AttachmentService attachments,
                                   TrainingPhotoService photos,
                                   UserRepository userRepository,
                                   MessageService msg) {
        this.repository = repository;
        this.commentRepository = commentRepository;
        this.deletionRepository = deletionRepository;
        this.access = access;
        this.unread = unread;
        this.attachments = attachments;
        this.photos = photos;
        this.userRepository = userRepository;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public CalendarRangeResponse getRange(UUID athleteId, LocalDate from, LocalDate to,
                                          UUID viewerId, boolean viewerIsAdmin) {
        access.requireAthlete(athleteId);
        validateRange(from, to);

        List<PersonalTraining> trainings = repository.findRange(athleteId, from, to);
        List<UUID> ids = trainings.stream().map(PersonalTraining::getId).toList();

        TrainingUnreadService.SeenMarker seen = unread.seenMarker(viewerId, athleteId);
        Set<UUID> withNewComments = unread.unreadTrainingIds(ids, viewerIsAdmin, seen);
        Map<UUID, Integer> commentCounts = commentCounts(ids);
        Map<UUID, List<AttachmentResponse>> materials = attachments.forTrainings(ids);

        LocalDateTime now = LocalDateTime.now();
        List<PersonalTrainingResponse> items = trainings.stream()
                .map(t -> toResponse(t, now,
                        isUnread(t, viewerIsAdmin, seen) || withNewComments.contains(t.getId()),
                        commentCounts.getOrDefault(t.getId(), 0),
                        materials.getOrDefault(t.getId(), List.of())))
                .toList();

        // The coach hears about the client's deletions and vice versa.
        List<DeletedTrainingNotice> notices = deletionRepository
                .findPending(athleteId, !viewerIsAdmin, PageRequest.of(0, MAX_DELETION_NOTICES)).stream()
                .map(d -> new DeletedTrainingNotice(d.getId(), d.getDate(), d.getStartTime(),
                        d.getTitle(), d.getDeletedAt()))
                .toList();

        return new CalendarRangeResponse(from, to, items, notices);
    }

    @Transactional
    public PersonalTrainingResponse create(UUID athleteId, CreateTrainingRequest request, boolean byAdmin) {
        User athlete = access.requireAthlete(athleteId);
        validateTimes(request.startTime(), request.endTime());

        // Absent kind means TRAINING: it is what almost every entry is, and what older clients send.
        TrainingKind kind = request.kind() == null ? TrainingKind.TRAINING : request.kind();
        PersonalTraining training =
                new PersonalTraining(athlete, kind, request.date(), request.title().trim(), byAdmin);
        training.edit(request.date(), request.startTime(), request.endTime(),
                request.title().trim(), Strings.trimToNull(request.description()), request.targetCalories(), byAdmin);
        PersonalTraining saved = repository.saveAndFlush(training);
        attachments.applyToTraining(saved, materialsFrom(request.attachments(), byAdmin));
        return single(saved);
    }

    @Transactional
    public PersonalTrainingResponse update(UUID trainingId, UpdateTrainingRequest request,
                                           UUID viewerId, boolean viewerIsAdmin) {
        PersonalTraining training = access.requireTraining(trainingId, viewerId, viewerIsAdmin);
        requireOwnEntry(training, viewerIsAdmin);
        requireCurrentVersion(training, request.version());
        validateTimes(request.startTime(), request.endTime());

        training.edit(request.date(), request.startTime(), request.endTime(),
                request.title().trim(), Strings.trimToNull(request.description()),
                request.targetCalories(), viewerIsAdmin);
        requireCompletedStaysInPast(training);
        PersonalTraining saved = save(training);
        // null here means "leave materials alone" — see AttachmentService for why that matters.
        attachments.applyToTraining(saved, materialsFrom(request.attachments(), viewerIsAdmin));
        return single(saved);
    }

    @Transactional
    public void delete(UUID trainingId, UUID viewerId, boolean viewerIsAdmin) {
        PersonalTraining training = access.requireTraining(trainingId, viewerId, viewerIsAdmin);
        requireOwnEntry(training, viewerIsAdmin);
        deleteAndAnnounce(training, viewerIsAdmin);
    }

    private void deleteAndAnnounce(PersonalTraining training, boolean viewerIsAdmin) {
        // Only FUTURE deletions are announced. Clearing out a past entry is housekeeping, and an
        // alert for it would train the other side to ignore the banner.
        if (training.getDate().isAfter(LocalDate.now())) {
            deletionRepository.save(new TrainingDeletion(training, viewerIsAdmin));
        }
        // Before the row goes: the comments cascade away in the database, so once delete() has run
        // nothing can find the photo files they pointed at. This class has no JPA cascade to hook.
        photos.purgeForTraining(training.getId());
        repository.delete(training);
    }

    /** Same content, shifted forward — "the same thing again next week" is the common case. */
    @Transactional
    public PersonalTrainingResponse duplicate(UUID trainingId, DuplicateTrainingRequest request,
                                              UUID viewerId, boolean viewerIsAdmin) {
        PersonalTraining source = access.requireTraining(trainingId, viewerId, viewerIsAdmin);
        requireOwnEntry(source, viewerIsAdmin);
        int offset = request.offsetDays() == null ? DEFAULT_DUPLICATE_OFFSET_DAYS : request.offsetDays();
        PersonalTraining copy = repository.saveAndFlush(
                copyOf(source, source.getAthlete(), source.getDate().plusDays(offset), viewerIsAdmin));
        attachments.copyBetweenTrainings(source.getId(), copy);
        return single(copy);
    }

    /**
     * Clipboard paste. COPY leaves the source alone; MOVE re-dates the original so its id — and with
     * it the completion state and the comment thread — survives, which a delete-and-recreate would lose.
     * <p>
     * Across two clients that re-dating is exactly what must NOT happen: the row carries the source
     * client's completion, effort rating and comment thread — health data about one person that would
     * reappear under another person's name. A cross-client MOVE is therefore a fresh copy for the
     * target plus a deletion of the original, announced to the source side like any other deletion.
     */
    @Transactional
    public PersonalTrainingResponse paste(PasteTrainingRequest request, UUID viewerId, boolean viewerIsAdmin) {
        PersonalTraining source = access.requireTraining(request.sourceId(), viewerId, viewerIsAdmin);
        User target = resolvePasteTarget(request.targetAthleteId(), source, viewerId, viewerIsAdmin);
        // After the target is settled, so naming someone else's calendar still answers 404 rather
        // than admitting the source exists.
        requireOwnEntry(source, viewerIsAdmin);
        boolean acrossAthletes = !target.getId().equals(source.getAthlete().getId());

        if (request.mode() == PasteMode.COPY || acrossAthletes) {
            PersonalTraining copy = copyOf(source, target, request.targetDate(), viewerIsAdmin);
            PersonalTraining saved = repository.saveAndFlush(copy);
            attachments.copyBetweenTrainings(source.getId(), saved);
            // A COPY leaves the original where it is, here as anywhere else; only a cut clears it.
            if (acrossAthletes && request.mode() == PasteMode.MOVE) {
                deleteAndAnnounce(source, viewerIsAdmin);
            }
            return single(saved);
        }

        source.edit(request.targetDate(), source.getStartTime(), source.getEndTime(),
                source.getTitle(), source.getDescription(), source.getTargetCalories(), viewerIsAdmin);
        requireCompletedStaysInPast(source);
        return single(save(source));
    }

    /**
     * Whose calendar a paste lands in.
     * <p>
     * An absent id means "the source's own athlete" — that is what a client always means and what an
     * older build of the coach's page sent. Naming someone else is the coach's privilege: a client
     * pointing at another person gets the same 404 the rest of the calendar gives, so the roster
     * cannot be probed from here either.
     */
    private User resolvePasteTarget(@Nullable UUID targetAthleteId, PersonalTraining source,
                                    UUID viewerId, boolean viewerIsAdmin) {
        if (targetAthleteId == null || targetAthleteId.equals(source.getAthlete().getId())) {
            return source.getAthlete();
        }
        if (!viewerIsAdmin && !targetAthleteId.equals(viewerId)) {
            throw new NotFoundException(msg.get("athlete.not.found"));
        }
        return access.requireAthlete(targetAthleteId);
    }

    /**
     * Ticking off is the client's act alone — the coach cannot mark someone else's session done, so
     * this has no counterpart on the admin controller.
     * <p>
     * A task is ticked off exactly like a training, minus the effort rating: "how hard was staying
     * under 2200 kcal, 1–10" is a question about nothing, and an answer would land in the same RPE
     * averages the coach reads training load from.
     */
    @Transactional
    public PersonalTrainingResponse complete(UUID trainingId, CompleteTrainingRequest request, UUID viewerId) {
        PersonalTraining training = access.requireTraining(trainingId, viewerId, false);
        LocalDateTime now = LocalDateTime.now();
        if (!training.hasStarted(now)) {
            throw new IllegalStateException(msg.get("personaltraining.not.started"));
        }
        // Which of the two rules applies depends on the row, so it cannot be a bean-validation
        // annotation on the request.
        if (training.isTask() && request.rpe() != null) {
            throw new IllegalArgumentException(msg.get("personaltraining.rpe.not.for.task"));
        }
        if (!training.isTask() && request.rpe() == null) {
            throw new IllegalArgumentException(msg.get("personaltraining.rpe.required"));
        }
        training.complete(request.rpe(), Strings.trimToNull(request.feedback()));
        return single(save(training));
    }

    @Transactional
    public PersonalTrainingResponse uncomplete(UUID trainingId, UUID viewerId) {
        PersonalTraining training = access.requireTraining(trainingId, viewerId, false);
        training.uncomplete();
        return single(save(training));
    }

    @Transactional(readOnly = true)
    public List<TrainingCommentResponse> getComments(UUID trainingId, UUID viewerId, boolean viewerIsAdmin) {
        access.requireTraining(trainingId, viewerId, viewerIsAdmin);
        return commentRepository.findThread(trainingId).stream()
                .map(c -> TrainingCommentMapper.toResponse(c, viewerId, viewerIsAdmin))
                .toList();
    }

    @Transactional
    public TrainingCommentResponse addComment(UUID trainingId, AddCommentRequest request,
                                              UUID viewerId, boolean viewerIsAdmin) {
        PersonalTraining training = access.requireTraining(trainingId, viewerId, viewerIsAdmin);
        User author = userRepository.findById(viewerId)
                .orElseThrow(() -> new IllegalStateException(msg.get("error.user.not.found")));
        TrainingComment comment = new TrainingComment(training, author, viewerIsAdmin, request.body().trim());
        return TrainingCommentMapper.toResponse(commentRepository.save(comment), viewerId, viewerIsAdmin);
    }

    @Transactional
    public void markSeen(UUID athleteId, UUID viewerId, boolean viewerIsAdmin,
                         LocalDate viewedThrough) {
        access.requireAthlete(athleteId);
        unread.markSeen(viewerId, athleteId, viewedThrough);
    }

    /**
     * Hides the deletion banner. Separate from "seen" — acknowledging a loss is its own act.
     * <p>
     * Unlike {@link #markSeen} this has no viewer id: the banner is dismissed for a whole side of
     * the conversation, not per person, because the notice is about the client's plan rather than
     * about who happened to open it.
     */
    @Transactional
    public void dismissDeletions(UUID athleteId, boolean viewerIsAdmin) {
        access.requireAthlete(athleteId);
        deletionRepository.dismissAll(athleteId, !viewerIsAdmin, Instant.now());
    }

    @Transactional(readOnly = true)
    public MyTrainingSummary summary(UUID athleteId, UUID viewerId, boolean viewerIsAdmin) {
        access.requireAthlete(athleteId);
        long unreadCount = unread.countUnread(viewerId, athleteId, viewerIsAdmin);
        int deleted = deletionRepository
                .findPending(athleteId, !viewerIsAdmin, PageRequest.of(0, MAX_DELETION_NOTICES)).size();
        return new MyTrainingSummary(unreadCount, deleted,
                repository.findNextTrainingDate(athleteId, LocalDate.now()));
    }

    private Map<UUID, Integer> commentCounts(List<UUID> trainingIds) {
        Map<UUID, Integer> counts = new HashMap<>();
        if (trainingIds.isEmpty()) {
            return counts;
        }
        for (Object[] row : commentRepository.countByTrainingIds(trainingIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    /**
     * Keyed on {@code updatedAt}, never {@code completedAt}: undoing a completion clears that column
     * and the coach still needs to hear about it.
     */
    /**
     * Mirrors {@code PersonalTrainingRepository.countTouchedSince} exactly — the badge counts what
     * these dots mark, so the two conditions cannot drift apart. Either half alone is wrong: without
     * the timestamp an old entry never stops shouting, without the reach a page the viewer never
     * opened is silently treated as read.
     */
    private static boolean isUnread(PersonalTraining t, boolean viewerIsAdmin,
                                    TrainingUnreadService.SeenMarker seen) {
        if (t.isLastModifiedByAdmin() == viewerIsAdmin) {
            return false;
        }
        return t.getUpdatedAt().isAfter(seen.at()) || t.getDate().isAfter(seen.reach());
    }

    private PersonalTrainingResponse single(PersonalTraining training) {
        // The viewer just performed this action, so nothing about it is news to them.
        return toResponse(training, LocalDateTime.now(), false,
                (int) commentRepository.countByTrainingId(training.getId()),
                attachments.forTrainings(List.of(training.getId()))
                        .getOrDefault(training.getId(), List.of()));
    }

    /**
     * @param athlete whose plan the copy belongs to — the source's own for a duplicate or a paste
     *                within one calendar, someone else's when the coach copies a session across.
     */
    private PersonalTraining copyOf(PersonalTraining source, User athlete, LocalDate date, boolean byAdmin) {
        PersonalTraining copy = new PersonalTraining(
                athlete, source.getKind(), date, source.getTitle(), byAdmin);
        // A copy is a fresh plan, never a fresh achievement: completion, RPE and feedback stay behind.
        copy.edit(date, source.getStartTime(), source.getEndTime(),
                source.getTitle(), source.getDescription(), source.getTargetCalories(), byAdmin);
        return copy;
    }

    private PersonalTraining save(PersonalTraining training) {
        try {
            return repository.saveAndFlush(training);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new IllegalStateException(msg.get("personaltraining.version.conflict"));
        }
    }

    /**
     * The client holds the version they loaded. Comparing it here — rather than relying on the
     * {@code @Version} column alone — is what makes the check work across separate HTTP requests:
     * within one transaction Hibernate would happily write over a row read moments earlier.
     */
    private void requireCurrentVersion(PersonalTraining training, Long expected) {
        if (expected == null || training.getVersion() != expected) {
            throw new IllegalStateException(msg.get("personaltraining.version.conflict"));
        }
    }

    /**
     * Materials are the coach's to set, so a client's save never touches them.
     * <p>
     * The library belongs to the coach and the client has no screen for it — no picker, no link
     * field, nothing. The rule therefore lived only in the form, and a request built by hand would
     * have been accepted: {@code AttachmentService} looks a clip up by id without asking who is
     * calling. Nothing leaks that way (a client can only name a clip already shown to them, and
     * there is no listing or search on their side of the API), but a clip they pin can no longer be
     * removed from the library — deletion is refused for anything in use, and the coach cannot see
     * where it is being used.
     * <p>
     * Ignoring the field rather than rejecting it is what keeps the normal case working. The form
     * always sends the full list, even with the section hidden, so a client re-dating a training the
     * coach had attached a clip to would send that clip's id straight back; an error there would
     * break an edit that had nothing to do with materials. Answering {@code null} means "leave them
     * alone", which is exactly right for both shapes: a client's new entry has none to begin with,
     * and an existing one keeps whatever the coach put there.
     */
    private @Nullable List<AttachmentRequest> materialsFrom(@Nullable List<AttachmentRequest> requested,
                                                            boolean viewerIsAdmin) {
        return viewerIsAdmin ? requested : null;
    }

    /**
     * The client may reshape only what they put in the plan themselves.
     * <p>
     * What the coach assigned is theirs to do, comment on and tick off — not to rewrite, copy away or
     * clear. The prescription is the coaching, and a plan the client can quietly edit stops being one:
     * the coach would be reading their own instructions back, changed, with nothing saying so. The
     * check covers deletion too, otherwise "delete and add my own version" walks straight around it.
     * <p>
     * Keyed on {@code createdByAdmin}, which is fixed at creation — never on who touched the row last,
     * which flips every time either side ticks something off. The coach is exempt: they reach every
     * row in the plan, including the ones the client logged themselves.
     */
    private void requireOwnEntry(PersonalTraining training, boolean viewerIsAdmin) {
        if (!viewerIsAdmin && training.isCreatedByAdmin()) {
            throw new IllegalStateException(msg.get("personaltraining.coach.readonly"));
        }
    }

    /**
     * A completed training describes something that happened. Letting it slide into the future would
     * produce a session that is both done and yet to come; undo the completion first.
     */
    private void requireCompletedStaysInPast(PersonalTraining training) {
        if (training.isCompleted() && training.start().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException(msg.get("personaltraining.completed.future"));
        }
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException(msg.get("personaltraining.range.invalid"));
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException(msg.get("personaltraining.range.too.long"));
        }
    }

    private void validateTimes(@Nullable LocalTime start, @Nullable LocalTime end) {
        if (start == null && end != null) {
            throw new IllegalArgumentException(msg.get("personaltraining.time.end.without.start"));
        }
        if (start != null && end != null && !end.isAfter(start)) {
            throw new IllegalArgumentException(msg.get("personaltraining.time.end.before.start"));
        }
    }



    static PersonalTrainingResponse toResponse(PersonalTraining t, LocalDateTime now,
                                               boolean unread, int commentCount,
                                               List<AttachmentResponse> materials) {
        return new PersonalTrainingResponse(
                t.getId(), t.getKind(), t.getDate(), t.getStartTime(), t.getEndTime(),
                t.getTitle(), t.getDescription(), t.getTargetCalories(), t.status(now),
                t.getCompletedAt(), t.getFeedback(), t.getRpe(),
                t.isCreatedByAdmin(), t.isLastModifiedByAdmin(),
                unread, commentCount, materials,
                t.getVersion(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
