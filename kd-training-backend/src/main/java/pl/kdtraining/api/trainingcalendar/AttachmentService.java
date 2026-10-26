package pl.kdtraining.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.api.NotFoundException;
import pl.kdtraining.api.Strings;
import pl.kdtraining.api.trainingcalendar.TrainingCalendarDtos.AttachmentRequest;
import pl.kdtraining.api.trainingcalendar.TrainingCalendarDtos.AttachmentResponse;
import pl.kdtraining.domain.training.*;
import pl.kdtraining.infrastructure.i18n.MessageService;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Materials attached to trainings and templates.
 *
 * <h2>The three-way contract</h2>
 * <pre>
 *   attachments == null  -> LEAVE ALONE
 *   attachments == []    -> CLEAR
 *   attachments == list  -> REPLACE with exactly that list
 * </pre>
 * The distinction is load-bearing. Re-dating a training sends the whole object, and treating a
 * missing list as "clear" would silently drop the materials that edit never touched — a bug the
 * reference implementation shipped and had to chase down later.
 */
@Service
public class AttachmentService {

    private final TrainingAttachmentRepository repository;
    private final ExerciseVideoRepository videoRepository;
    private final MessageService msg;

    public AttachmentService(TrainingAttachmentRepository repository,
                             ExerciseVideoRepository videoRepository,
                             MessageService msg) {
        this.repository = repository;
        this.videoRepository = videoRepository;
        this.msg = msg;
    }

    @Transactional
    public void applyToTraining(PersonalTraining training, @Nullable List<AttachmentRequest> requests) {
        if (requests == null) {
            return;
        }
        repository.deleteForTraining(training.getId());
        // Flush before inserting: the partial unique index on (owner, position) would otherwise see
        // the old rows still present and reject the replacements.
        repository.flush();
        int position = 0;
        for (AttachmentRequest request : requests) {
            TrainingAttachment attachment = build(request, position++);
            attachment.attachTo(training);
            repository.save(attachment);
        }
    }

    @Transactional
    public void applyToTemplate(TrainingTemplate template, @Nullable List<AttachmentRequest> requests) {
        if (requests == null) {
            return;
        }
        repository.deleteForTemplate(template.getId());
        repository.flush();
        int position = 0;
        for (AttachmentRequest request : requests) {
            TrainingAttachment attachment = build(request, position++);
            attachment.attachTo(template);
            repository.save(attachment);
        }
    }

    /**
     * Carries the materials of one training onto a copy of it.
     * <p>
     * A copied session without its materials is a title and nothing else — the videos are the plan.
     * A VIDEO copy points at the same library row (correcting the name there still fixes it
     * everywhere); a LINK is duplicated, since it belongs to the training rather than to a library.
     */
    @Transactional
    public void copyBetweenTrainings(UUID sourceTrainingId, PersonalTraining target) {
        int position = 0;
        for (TrainingAttachment source : repository.findForTrainings(List.of(sourceTrainingId))) {
            ExerciseVideo video = source.getVideo();
            TrainingAttachment copy;
            if (video != null) {
                copy = TrainingAttachment.video(video, source.getLabel(), position++);
            } else if (source.getUrl() != null) {
                copy = TrainingAttachment.link(source.getUrl(), source.getLabel(), position++);
            } else {
                continue;
            }
            copy.attachTo(target);
            repository.save(copy);
        }
    }

    /** Materials for a whole calendar page in one query. */
    @Transactional(readOnly = true)
    public Map<UUID, List<AttachmentResponse>> forTrainings(List<UUID> trainingIds) {
        Map<UUID, List<AttachmentResponse>> byTraining = new HashMap<>();
        if (trainingIds.isEmpty()) {
            return byTraining;
        }
        for (TrainingAttachment attachment : repository.findForTrainings(trainingIds)) {
            PersonalTraining owner = attachment.getTrainingOwner();
            if (owner == null) continue;
            byTraining.computeIfAbsent(owner.getId(), k -> new ArrayList<>()).add(toResponse(attachment));
        }
        return byTraining;
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> forTemplate(UUID templateId) {
        return repository.findForTemplate(templateId).stream().map(AttachmentService::toResponse).toList();
    }

    private TrainingAttachment build(AttachmentRequest request, int position) {
        return switch (request.kind()) {
            case LINK -> {
                String url = requireHttpUrl(request.url());
                yield TrainingAttachment.link(url, Strings.trimToNull(request.label()), position);
            }
            case VIDEO -> {
                if (request.videoId() == null) {
                    throw new IllegalArgumentException(msg.get("trainingattachment.video.required"));
                }
                ExerciseVideo video = videoRepository.findById(request.videoId())
                        .orElseThrow(() -> new NotFoundException(msg.get("exercisevideo.not.found")));
                yield TrainingAttachment.video(video, Strings.trimToNull(request.label()), position);
            }
        };
    }

    /** Anything that will end up in an href has to be a real http(s) address, not "javascript:…". */
    private String requireHttpUrl(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(msg.get("trainingattachment.url.required"));
        }
        String url = raw.trim();
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
                throw new IllegalArgumentException(msg.get("trainingattachment.url.invalid"));
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(msg.get("trainingattachment.url.invalid"));
        }
        return url;
    }


    static AttachmentResponse toResponse(TrainingAttachment a) {
        ExerciseVideo video = a.getVideo();
        if (video == null) {
            return new AttachmentResponse(a.getId(), a.getKind(), a.getLabel(), a.getUrl(),
                    null, null, null, null);
        }
        YouTubeUrl parsed = new YouTubeUrl(video.getVideoKey());
        return new AttachmentResponse(a.getId(), a.getKind(),
                a.getLabel() != null ? a.getLabel() : video.getName(),
                video.getUrl(), video.getId(), video.getName(),
                parsed.embedUrl(), parsed.thumbnailUrl());
    }
}
