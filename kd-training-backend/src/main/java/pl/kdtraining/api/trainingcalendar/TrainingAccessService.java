package pl.kdtraining.api.trainingcalendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.api.NotFoundException;
import pl.kdtraining.domain.training.PersonalTraining;
import pl.kdtraining.domain.training.PersonalTrainingRepository;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRepository;
import pl.kdtraining.infrastructure.i18n.MessageService;

import java.util.UUID;

/**
 * The single gate for every training-calendar entry point.
 * <p>
 * Two rules live here and nowhere else:
 * <ul>
 *   <li>an account that is not flagged as a 1-on-1 client is indistinguishable from one that does
 *       not exist — same 404, same wording, so the roster cannot be probed;</li>
 *   <li>someone else's training is indistinguishable from a non-existent one, for the same reason.
 *       A 403 here would confirm the id is real.</li>
 * </ul>
 */
@Service
public class TrainingAccessService {

    private final UserRepository userRepository;
    private final PersonalTrainingRepository trainingRepository;
    private final MessageService msg;

    public TrainingAccessService(UserRepository userRepository,
                                 PersonalTrainingRepository trainingRepository,
                                 MessageService msg) {
        this.userRepository = userRepository;
        this.trainingRepository = trainingRepository;
        this.msg = msg;
    }

    /** Resolves a flagged 1-on-1 client, or 404. */
    @Transactional(readOnly = true)
    public User requireAthlete(UUID athleteId) {
        User user = userRepository.findById(athleteId).orElseThrow(this::athleteNotFound);
        if (!user.isAthlete()) {
            throw athleteNotFound();
        }
        return user;
    }

    /**
     * Resolves a training the caller may act on.
     *
     * @param viewerIsAdmin the coach reaches every client's plan; a client reaches only their own
     */
    @Transactional(readOnly = true)
    public PersonalTraining requireTraining(UUID trainingId, UUID viewerId, boolean viewerIsAdmin) {
        PersonalTraining training = trainingRepository.findById(trainingId)
                .orElseThrow(this::trainingNotFound);
        if (!viewerIsAdmin && !training.getAthlete().getId().equals(viewerId)) {
            throw trainingNotFound();
        }
        // A client whose flag was cleared keeps their rows but loses access to them.
        if (!training.getAthlete().isAthlete()) {
            throw trainingNotFound();
        }
        return training;
    }

    private NotFoundException athleteNotFound() {
        return new NotFoundException(msg.get("athlete.not.found"));
    }

    private NotFoundException trainingNotFound() {
        return new NotFoundException(msg.get("personaltraining.not.found"));
    }
}
