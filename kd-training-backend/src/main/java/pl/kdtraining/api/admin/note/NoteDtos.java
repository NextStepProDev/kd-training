package pl.kdtraining.api.admin.note;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.kdtraining.domain.adminnote.AdminPrivateNote;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Shapes that exist only for the notebook.
 *
 * <p>They are separate types rather than fields on the calendar records on purpose -- see
 * {@link AdminPrivateNote} for why an obliging field is the real risk here.
 */
public final class NoteDtos {

    private NoteDtos() {
    }

    /**
     * The note, or the absence of one.
     *
     * <p>A missing note answers 200 with both fields null rather than 204, so the frontend never has
     * to read meaning into an empty body.
     */
    public record NoteResponse(@Nullable String body, @Nullable Instant updatedAt) {

        static final NoteResponse EMPTY = new NoteResponse(null, null);
    }

    public record SaveNoteRequest(
        @Size(max = AdminPrivateNote.MAX_BODY_LENGTH, message = "{validation.adminnote.body.length}")
        String body
    ) {}

    /** Identifiers only. Whatever else changes here, the text must never join them. */
    public record NoteMarkersResponse(List<UUID> trainingIds) {}

    /**
     * One note as handed over to the person it is about (GDPR art. 15). Leaves the notebook only
     * through {@link AdminPrivateNoteService#exportForAthlete}, and never inside a calendar shape.
     */
    public record NoteExport(
        NoteTarget target,
        LocalDate date,
        @Nullable String trainingTitle,
        String body,
        Instant createdAt,
        Instant updatedAt
    ) {}
}
