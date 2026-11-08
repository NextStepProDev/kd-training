package pl.mocnygrzbiet.api.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.mocnygrzbiet.api.NotFoundException;
import pl.mocnygrzbiet.api.admin.note.AdminPrivateNoteService;
import pl.mocnygrzbiet.api.trainingcalendar.TrainingPhotoService;
import pl.mocnygrzbiet.domain.training.AthleteGoalRepository;
import pl.mocnygrzbiet.domain.training.AthleteMaterialRepository;
import pl.mocnygrzbiet.domain.training.PersonalTrainingRepository;
import pl.mocnygrzbiet.domain.training.TrainingCalendarReadRepository;
import pl.mocnygrzbiet.domain.training.TrainingDeletionRepository;
import pl.mocnygrzbiet.domain.user.User;
import pl.mocnygrzbiet.domain.user.UserRepository;
import pl.mocnygrzbiet.infrastructure.i18n.MessageService;

import java.util.UUID;

/**
 * Erases one person's 1-on-1 training plan, permanently, without touching anything else.
 *
 * <h2>Why this exists as its own action</h2>
 * Clearing the {@code is_athlete} flag hides the plan and deletes nothing — deliberately, so a break
 * in the coaching can be undone (users.is_athlete and training_consent_at in V1). That leaves no way to actually remove the data, and the
 * gap is worse than it looks: with the flag gone the coach's calendar is unreachable, so even the
 * per-item bins disappear from the panel. The API allowed a private note to be deleted after the flag
 * was cleared; the interface offered nowhere to click. This closes that, and gives "we are finished"
 * a different button from "we are pausing".
 *
 * <h2>What it must never touch</h2>
 * The account itself stays. Only the 1-on-1 plan goes.
 */
@Service
public class AdminTrainingPlanErasureService {

    private static final Logger log = LoggerFactory.getLogger(AdminTrainingPlanErasureService.class);

    private final UserRepository userRepository;
    private final PersonalTrainingRepository trainings;
    private final AthleteGoalRepository goals;
    private final AthleteMaterialRepository materials;
    private final TrainingCalendarReadRepository reads;
    private final TrainingDeletionRepository deletions;
    private final TrainingPhotoService photos;
    private final AdminPrivateNoteService notes;
    private final MessageService msg;

    public AdminTrainingPlanErasureService(UserRepository userRepository,
                                           PersonalTrainingRepository trainings,
                                           AthleteGoalRepository goals,
                                           AthleteMaterialRepository materials,
                                           TrainingCalendarReadRepository reads,
                                           TrainingDeletionRepository deletions,
                                           TrainingPhotoService photos,
                                           AdminPrivateNoteService notes,
                                           MessageService msg) {
        this.userRepository = userRepository;
        this.trainings = trainings;
        this.goals = goals;
        this.materials = materials;
        this.reads = reads;
        this.deletions = deletions;
        this.photos = photos;
        this.notes = notes;
        this.msg = msg;
    }

    /**
     * @param userId whose plan to erase. Works whether or not the flag is still set — an account that
     *               lost its athlete status is exactly the one whose data would otherwise be stranded.
     * @param actingAdminId only shapes the note count in the answer; the erasure itself is complete
     *                      regardless of who asks for it.
     */
    @Transactional
    public ErasedPlan erase(UUID userId, UUID actingAdminId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));

        // Files first, while the rows that name them still exist. Comment rows themselves leave
        // through the cascade when the trainings go, and Hibernate never loads them — so nothing
        // after this point could find the filenames.
        photos.purgeForAthlete(userId);

        int erasedNotes = notes.countOwnNotesBeforeErasure(userId, actingAdminId);
        int erasedTrainings = trainings.deleteAllForAthlete(userId);
        int erasedGoals = goals.deleteAllForAthlete(userId);
        // The questions under them leave through the cascade.
        int erasedMaterials = materials.deleteAllForAthlete(userId);
        deletions.deleteAllForAthlete(userId);
        reads.deleteAllForAthlete(userId);

        // The flag and the consent go together, and the consent must not survive the data it covered:
        // re-enabling the plan later has to start from a fresh, deliberate agreement.
        user.setAthlete(false);
        userRepository.save(user);

        log.info("Erased the 1-on-1 plan for user {}: {} trainings, {} goals, {} materials, {} notes",
            userId, erasedTrainings, erasedGoals, erasedMaterials, erasedNotes);

        return new ErasedPlan(erasedTrainings, erasedGoals, erasedNotes);
    }

    public record ErasedPlan(int trainings, int goals, int notes) {}
}
