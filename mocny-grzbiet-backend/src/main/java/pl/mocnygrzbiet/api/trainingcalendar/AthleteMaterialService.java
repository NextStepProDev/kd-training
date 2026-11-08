package pl.mocnygrzbiet.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.mocnygrzbiet.api.NotFoundException;
import pl.mocnygrzbiet.api.Strings;
import pl.mocnygrzbiet.api.trainingcalendar.TrainingCalendarDtos.*;
import pl.mocnygrzbiet.domain.training.AthleteMaterial;
import pl.mocnygrzbiet.domain.training.AthleteMaterialRepository;
import pl.mocnygrzbiet.domain.training.ExerciseVideo;
import pl.mocnygrzbiet.domain.training.ExerciseVideoRepository;
import pl.mocnygrzbiet.domain.training.MaterialQuestion;
import pl.mocnygrzbiet.domain.training.MaterialQuestionRepository;
import pl.mocnygrzbiet.domain.user.User;
import pl.mocnygrzbiet.domain.user.UserRepository;
import pl.mocnygrzbiet.infrastructure.i18n.MessageService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Materials the coach gives one client to keep (the client's answers: "na stałe", questions "przy
 * materiale"). The coach writes them; the client reads them and asks questions under each one.
 * <p>
 * Reach follows the goals: a material of someone whose client flag is off is out of reach for both
 * sides, and one that is not yours answers the same 404 as one that does not exist.
 */
@Service
public class AthleteMaterialService {

    private final AthleteMaterialRepository materials;
    private final MaterialQuestionRepository questions;
    private final ExerciseVideoRepository videos;
    private final UserRepository users;
    private final TrainingAccessService access;
    private final TrainingUnreadService unread;
    private final MessageService msg;
    private final CoachReplyNotifier replyNotifier;

    public AthleteMaterialService(AthleteMaterialRepository materials, MaterialQuestionRepository questions,
                                  ExerciseVideoRepository videos, UserRepository users,
                                  TrainingAccessService access, TrainingUnreadService unread,
                                  MessageService msg, CoachReplyNotifier replyNotifier) {
        this.replyNotifier = replyNotifier;
        this.materials = materials;
        this.questions = questions;
        this.videos = videos;
        this.users = users;
        this.access = access;
        this.unread = unread;
        this.msg = msg;
    }

    /**
     * The client's materials with their threads. "New" comes off the same read marker as the rest of
     * the plan page, so the badge and the marks on the cards clear together.
     */
    @Transactional(readOnly = true)
    public List<MaterialResponse> list(UUID athleteId, UUID viewerId, boolean viewerIsAdmin) {
        access.requireAthlete(athleteId);
        Instant since = unread.seenMarker(viewerId, athleteId).at();
        List<AthleteMaterial> found = materials.findForAthlete(athleteId);
        Map<UUID, List<MaterialQuestion>> threads = found.isEmpty() ? Map.of()
                : questions.findForMaterials(found.stream().map(AthleteMaterial::getId).toList()).stream()
                        .collect(Collectors.groupingBy(q -> q.getMaterial().getId()));
        return found.stream().map(m -> {
            List<MaterialQuestion> thread = threads.getOrDefault(m.getId(), List.of());
            // The coach wrote the material, so only the client can find it new; a message is new to
            // whoever did not write it.
            boolean isNew = (!viewerIsAdmin && m.getCreatedAt().isAfter(since))
                    || thread.stream().anyMatch(q -> q.isAuthorIsAdmin() != viewerIsAdmin && q.getCreatedAt().isAfter(since));
            return toResponse(m, thread, isNew);
        }).toList();
    }

    @Transactional
    public MaterialResponse create(UUID athleteId, MaterialRequest request) {
        User athlete = access.requireAthlete(athleteId);
        String note = Strings.trimToNull(request.note());
        ExerciseVideo video = resolveVideo(request.videoId(), note);
        AthleteMaterial material = materials.save(new AthleteMaterial(athlete, request.title().trim(), note, video));
        return toResponse(material, List.of(), null);
    }

    @Transactional
    public MaterialResponse update(UUID materialId, MaterialRequest request) {
        AthleteMaterial material = requireMaterial(materialId);
        String note = Strings.trimToNull(request.note());
        material.edit(request.title().trim(), note, resolveVideo(request.videoId(), note));
        return toResponse(materials.save(material), questions.findForMaterials(List.of(materialId)), null);
    }

    @Transactional
    public void delete(UUID materialId) {
        materials.delete(requireMaterial(materialId));
    }

    /**
     * A question from the client, or the coach's reply.
     *
     * @param viewerIsAdmin the coach may write under any client's material; a client only under their own
     */
    @Transactional
    public MaterialQuestionResponse ask(UUID materialId, UUID authorId, boolean viewerIsAdmin, MaterialQuestionRequest request) {
        AthleteMaterial material = requireMaterial(materialId);
        if (!viewerIsAdmin && !material.getAthlete().getId().equals(authorId)) {
            throw notFound();
        }
        User author = users.getReferenceById(authorId);
        MaterialQuestion question = questions.save(
                new MaterialQuestion(material, author, viewerIsAdmin, request.body().trim()));
        if (viewerIsAdmin) {
            replyNotifier.coachWroteTo(material.getAthlete());
        }
        return toResponse(question);
    }

    /** No video and no note would leave the client a bare title — the database refuses it too. */
    private @Nullable ExerciseVideo resolveVideo(@Nullable UUID videoId, @Nullable String note) {
        if (videoId == null) {
            if (note == null) {
                throw new IllegalArgumentException(msg.get("material.empty"));
            }
            return null;
        }
        return videos.findById(videoId)
                .orElseThrow(() -> new NotFoundException(msg.get("exercisevideo.not.found")));
    }

    private AthleteMaterial requireMaterial(UUID materialId) {
        AthleteMaterial material = materials.findById(materialId).orElseThrow(this::notFound);
        if (!material.getAthlete().isAthlete()) {
            throw notFound();
        }
        return material;
    }

    private NotFoundException notFound() {
        return new NotFoundException(msg.get("material.not.found"));
    }

    private static MaterialResponse toResponse(AthleteMaterial m, List<MaterialQuestion> thread, @Nullable Boolean unread) {
        ExerciseVideo video = m.getVideo();
        return new MaterialResponse(m.getId(), m.getTitle(), m.getNote(),
                video == null ? null : ExerciseVideoService.toResponse(video),
                m.getCreatedAt(), thread.stream().map(AthleteMaterialService::toResponse).toList(), unread);
    }

    private static MaterialQuestionResponse toResponse(MaterialQuestion q) {
        return new MaterialQuestionResponse(q.getId(), q.isAuthorIsAdmin(), q.getBody(), q.getCreatedAt());
    }
}
