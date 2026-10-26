package pl.kdtraining.api.admin.note;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.api.admin.note.NoteDtos.NoteExport;
import pl.kdtraining.api.admin.note.NoteDtos.NoteMarkersResponse;
import pl.kdtraining.api.admin.note.NoteDtos.NoteResponse;
import pl.kdtraining.api.trainingcalendar.TrainingAccessService;
import pl.kdtraining.domain.adminnote.AdminPrivateNote;
import pl.kdtraining.domain.adminnote.AdminPrivateNoteRepository;
import pl.kdtraining.infrastructure.i18n.MessageService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * The owner's private notebook: one note per author per training.
 */
@Service
public class AdminPrivateNoteService {

    /** Same ceiling as the calendar page, and the same message when it is exceeded. */
    private static final int MAX_MARKER_RANGE_DAYS = 62;

    private final AdminPrivateNoteRepository notes;
    private final TrainingAccessService access;
    private final MessageService msg;

    public AdminPrivateNoteService(AdminPrivateNoteRepository notes,
                                   TrainingAccessService access,
                                   MessageService msg) {
        this.notes = notes;
        this.access = access;
        this.msg = msg;
    }

    /**
     * Everything the URL carries is validated here and nowhere else, so a new operation cannot
     * accidentally skip a gate: the training must exist and belong to a flagged athlete, behind the
     * same 404-never-403 gate as the rest of the panel.
     */
    private void requireTarget(NoteTarget target, UUID id, UUID adminId) {
        switch (target) {
            case TRAINING -> access.requireTraining(id, adminId, true);
        }
    }

    @Transactional(readOnly = true)
    public NoteResponse get(NoteTarget target, UUID id, UUID adminId) {
        requireTarget(target, id, adminId);
        return notes.findForTraining(adminId, id)
            .map(note -> new NoteResponse(note.getBody(), note.getUpdatedAt()))
            .orElse(NoteResponse.EMPTY);
    }

    @Transactional
    public void save(NoteTarget target, UUID id, UUID adminId, String rawBody) {
        requireTarget(target, id, adminId);
        notes.upsertForTraining(adminId, id, sanitize(rawBody), Instant.now());
    }

    /**
     * Deliberately does NOT call {@link #requireTarget}.
     *
     * <p>Reads and writes are gated; deleting is not, and that is a correction rather than an
     * oversight. Removing your own text cannot expose anything, and a query narrowed to
     * (author, target) can only reach the row the caller wrote. Giving all three operations the same
     * guard looks safe and is not: clearing somebody's athlete flag would make the notes about their
     * trainings invisible AND undeletable at once, because the coach's calendar is unreachable
     * without the flag and the gate would refuse the one operation that could tidy them away.
     */
    @Transactional
    public void delete(NoteTarget target, UUID id, UUID adminId) {
        switch (target) {
            case TRAINING -> notes.deleteForTraining(adminId, id);
        }
    }

    /**
     * Counts the caller's notes about one athlete, as part of erasing their 1-on-1 plan.
     * <p>
     * This is the seam other packages use: the entity and its repository stay unreachable outside
     * this package (see the isolation gate). The notes themselves need no statement — they leave
     * through the cascade when the trainings go — but they have to be COUNTED first, and once the
     * trainings are gone there is nothing left to count them from. Must run BEFORE the trainings are
     * deleted.
     *
     * @return how many of the caller's OWN notes go. Everybody's are deleted — erasure of a person's
     *         data cannot be selective — but the count must not describe somebody else's notebook,
     *         or the reply becomes a way to discover that one exists.
     */
    @Transactional(readOnly = true)
    public int countOwnNotesBeforeErasure(UUID athleteId, UUID actingAdminId) {
        return notes.countAboutAthleteTrainingsByAuthor(athleteId, actingAdminId);
    }

    /**
     * Every note about one person, from every author, for a GDPR access request (art. 15).
     * <p>
     * The other seam out of the notebook, next to {@link #countOwnNotesBeforeErasure}: the subject is entitled
     * to everything held about them, so this one is not scoped to the caller either. It hands out
     * plain values rather than the entity, so the fence around {@link AdminPrivateNote} stays whole.
     */
    @Transactional(readOnly = true)
    public List<NoteExport> exportForAthlete(UUID athleteId) {
        return notes.findAllAboutAthlete(athleteId).stream()
            .map(n -> new NoteExport(NoteTarget.TRAINING, n.getTraining().getDate(),
                n.getTraining().getTitle(), n.getBody(), n.getCreatedAt(), n.getUpdatedAt()))
            .toList();
    }

    @Transactional(readOnly = true)
    public NoteMarkersResponse markers(UUID adminId, LocalDate from, LocalDate to, UUID athleteId) {
        validateRange(from, to);
        access.requireAthlete(athleteId);
        return new NoteMarkersResponse(notes.markedTrainingIds(adminId, athleteId, from, to));
    }

    /**
     * Trim, and deliberately no HTML escaping.
     *
     * <p>Escaping on the way in turns the author's own quotes and apostrophes into entities, and then
     * every reader needs to undo that at render time. This text never reaches an email or an
     * {@code innerHTML}; its only author and its only reader are the same person.
     *
     * <p>Length is NOT clipped here. {@code @Size} on the request already answers an over-long note
     * with a 400, and silently truncating would be a second, different answer to the same question —
     * the one that loses the end of somebody's paragraph without saying so.
     */
    private String sanitize(@Nullable String rawBody) {
        String body = rawBody == null ? "" : rawBody.trim();
        if (body.isEmpty()) {
            throw new IllegalArgumentException(msg.get("adminnote.body.empty"));
        }
        return body;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException(msg.get("personaltraining.range.invalid"));
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_MARKER_RANGE_DAYS) {
            throw new IllegalArgumentException(msg.get("personaltraining.range.too.long"));
        }
    }
}
