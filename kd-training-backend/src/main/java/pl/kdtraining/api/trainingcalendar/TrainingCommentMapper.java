package pl.kdtraining.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import pl.kdtraining.api.trainingcalendar.TrainingCalendarDtos.TrainingCommentPhoto;
import pl.kdtraining.api.trainingcalendar.TrainingCalendarDtos.TrainingCommentResponse;
import pl.kdtraining.domain.training.TrainingComment;
import pl.kdtraining.domain.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Renders a comment for one particular viewer.
 * <p>
 * Two services build this shape — {@link PersonalTrainingService} for the thread and plain text
 * replies, {@link TrainingPhotoService} for replies carrying a photo — and both must produce
 * byte-identical payloads, because a single frontend component renders whatever comes back. Hence
 * one mapper rather than a copy on each side.
 * <p>
 * The viewer is a parameter, not an afterthought: the photo URL points at the coach's endpoint or
 * the client's depending on who asked, and {@code canDelete} is a statement about this reader.
 */
final class TrainingCommentMapper {

    private static final String COACH_PHOTO_PATH = "/api/admin/personal-trainings/comments/%s/photo";
    private static final String ATHLETE_PHOTO_PATH = "/api/user/my-training/comments/%s/photo";

    private TrainingCommentMapper() {}

    static TrainingCommentResponse toResponse(TrainingComment c, UUID viewerId, boolean viewerIsAdmin) {
        User author = c.getAuthor();
        return new TrainingCommentResponse(
                c.getId(), c.getBody(), c.isAuthorIsAdmin(),
                author == null ? null : author.getFirstName(), c.getCreatedAt(),
                toPhoto(c, viewerId, viewerIsAdmin));
    }

    @Nullable
    private static TrainingCommentPhoto toPhoto(TrainingComment c, UUID viewerId, boolean viewerIsAdmin) {
        Instant expiresAt = c.getPhotoExpiresAt();
        if (!c.hasPhoto() || expiresAt == null) {
            return null;
        }
        String path = viewerIsAdmin ? COACH_PHOTO_PATH : ATHLETE_PHOTO_PATH;
        return new TrainingCommentPhoto(
                path.formatted(c.getId()),
                c.getPhotoWidth() == null ? 0 : c.getPhotoWidth(),
                c.getPhotoHeight() == null ? 0 : c.getPhotoHeight(),
                expiresAt,
                canDelete(c, viewerId, viewerIsAdmin));
    }

    /**
     * The author can always withdraw what they sent — a screenshot that turned out to show more than
     * intended has to be removable, and comments have no delete of their own. The coach can remove
     * anything in their client's thread, because they are the one answering for what the club holds.
     */
    static boolean canDelete(TrainingComment c, UUID viewerId, boolean viewerIsAdmin) {
        if (viewerIsAdmin) {
            return true;
        }
        User author = c.getAuthor();
        return author != null && author.getId().equals(viewerId);
    }
}
