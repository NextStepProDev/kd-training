package pl.kdtraining.api.trainingcalendar;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.kdtraining.api.trainingcalendar.TrainingCalendarDtos.AthleteSummary;
import pl.kdtraining.config.CurrentUserId;

import java.util.List;
import java.util.UUID;

/**
 * Lives in the training-calendar package rather than {@code api.admin} so it can share DTO records
 * with the client-side controller. Role protection is unaffected — it comes from the path prefix
 * ({@code /api/admin/**} → ROLE_ADMIN in SecurityConfig), not from the package.
 */
@RestController
@RequestMapping("/api/admin/athletes")
public class AdminAthleteController {

    private final AdminAthleteService service;

    public AdminAthleteController(AdminAthleteService service) {
        this.service = service;
    }

    @GetMapping
    public List<AthleteSummary> list(@CurrentUserId UUID adminId) {
        return service.list(adminId);
    }
}
