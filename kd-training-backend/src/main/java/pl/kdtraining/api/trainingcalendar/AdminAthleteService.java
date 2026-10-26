package pl.kdtraining.api.trainingcalendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.api.trainingcalendar.TrainingCalendarDtos.AthleteSummary;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Coach-side roster of 1-on-1 clients — the entry point into a single person's calendar. */
@Service
public class AdminAthleteService {

    private static final String AVATAR_FOLDER = "avatars";

    private final UserRepository userRepository;
    private final TrainingUnreadService unread;

    public AdminAthleteService(UserRepository userRepository, TrainingUnreadService unread) {
        this.userRepository = userRepository;
        this.unread = unread;
    }

    /**
     * Clients with anything new listed first, so the coach's attention goes where it is needed
     * rather than to whoever happens to be alphabetically first.
     */
    @Transactional(readOnly = true)
    public List<AthleteSummary> list(UUID adminId) {
        List<User> athletes = userRepository.findByAthleteTrueOrderByFirstNameAscLastNameAsc();
        if (athletes.isEmpty()) {
            return List.of();
        }
        // Batched on purpose: counting per athlete would be 1 + 4N queries on a page the coach
        // opens constantly, and it would get slower with every client they take on.
        Map<UUID, Long> unreadByAthlete = unread.countUnreadForRoster(
                adminId, athletes.stream().map(User::getId).toList(), true);

        return athletes.stream()
                .map(user -> toSummary(user, unreadByAthlete.getOrDefault(user.getId(), 0L)))
                .sorted(Comparator.comparingLong(AthleteSummary::unreadCount).reversed())
                .toList();
    }

    private static AthleteSummary toSummary(User user, long unreadCount) {
        String avatarUrl = user.getAvatarFilename() != null
                ? "/api/files/" + AVATAR_FOLDER + "/" + user.getAvatarFilename()
                : null;
        return new AthleteSummary(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), avatarUrl, unreadCount);
    }
}
