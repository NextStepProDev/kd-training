package pl.kdtraining.api.trainingcalendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.api.NotFoundException;
import pl.kdtraining.api.Strings;
import pl.kdtraining.api.trainingcalendar.TrainingCalendarDtos.*;
import pl.kdtraining.domain.training.TrainingTemplate;
import pl.kdtraining.domain.training.TrainingTemplateRepository;
import pl.kdtraining.infrastructure.i18n.MessageService;

import java.util.List;
import java.util.UUID;

/**
 * Reusable training skeletons.
 * <p>
 * Applying a template copies its content into the new training rather than linking to it, so editing
 * a template never rewrites sessions already handed out — those describe what somebody actually did.
 * The copy happens on the frontend, which is why there is no "apply" endpoint here.
 */
@Service
public class TrainingTemplateService {

    private final TrainingTemplateRepository repository;
    private final AttachmentService attachments;
    private final MessageService msg;

    public TrainingTemplateService(TrainingTemplateRepository repository,
                                   AttachmentService attachments,
                                   MessageService msg) {
        this.repository = repository;
        this.attachments = attachments;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public List<TrainingTemplateResponse> list() {
        return repository.findAllByOrderByTitleAsc().stream()
                .map(t -> toResponse(t, attachments.forTemplate(t.getId())))
                .toList();
    }

    @Transactional
    public TrainingTemplateResponse create(TrainingTemplateRequest request) {
        TrainingTemplate template = new TrainingTemplate(request.title().trim(),
                Strings.trimToNull(request.description()), request.defaultDurationMinutes());
        TrainingTemplate saved = repository.saveAndFlush(template);
        attachments.applyToTemplate(saved, request.attachments());
        return toResponse(saved, attachments.forTemplate(saved.getId()));
    }

    @Transactional
    public TrainingTemplateResponse update(UUID id, TrainingTemplateRequest request) {
        TrainingTemplate template = require(id);
        template.edit(request.title().trim(), Strings.trimToNull(request.description()),
                request.defaultDurationMinutes());
        TrainingTemplate saved = repository.saveAndFlush(template);
        attachments.applyToTemplate(saved, request.attachments());
        return toResponse(saved, attachments.forTemplate(saved.getId()));
    }

    @Transactional
    public void delete(UUID id) {
        // Attachments cascade with the template; the videos they point at are untouched.
        repository.delete(require(id));
    }

    private TrainingTemplate require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException(msg.get("trainingtemplate.not.found")));
    }


    private static TrainingTemplateResponse toResponse(TrainingTemplate t, List<AttachmentResponse> materials) {
        return new TrainingTemplateResponse(t.getId(), t.getTitle(), t.getDescription(),
                t.getDefaultDurationMinutes(), materials);
    }
}
