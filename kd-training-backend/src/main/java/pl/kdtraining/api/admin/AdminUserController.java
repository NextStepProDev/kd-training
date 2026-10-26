package pl.kdtraining.api.admin;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.kdtraining.api.admin.AdminUserDtos.*;
import pl.kdtraining.config.CurrentUserId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService service;
    private final AdminTrainingPlanErasureService planErasure;
    private final AdminUserDataExportService dataExport;

    public AdminUserController(AdminUserService service,
                               AdminTrainingPlanErasureService planErasure,
                               AdminUserDataExportService dataExport) {
        this.service = service;
        this.planErasure = planErasure;
        this.dataExport = dataExport;
    }

    @GetMapping
    public PagedUsersResponse list(@RequestParam(required = false) @Nullable String search,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size,
                                   @RequestParam(defaultValue = "created") String sort,
                                   @RequestParam(defaultValue = "desc") String direction) {
        return service.list(search, page, size, sort, direction);
    }

    @GetMapping("/{id}")
    public AdminUserDetailResponse getDetail(@PathVariable UUID id) {
        return service.getDetail(id);
    }

    @PostMapping("/email")
    public SendEmailResponse sendEmail(@Valid @RequestBody SendEmailRequest request) {
        return service.sendEmail(request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUserId UUID adminId, @PathVariable UUID id,
                                       @RequestParam(defaultValue = "true") boolean notify) {
        service.delete(adminId, id, notify);
        return ResponseEntity.noContent().build();
    }

    /**
     * Everything held about one person as a ZIP, for a GDPR access request (art. 15/20). The data is
     * read up front, so a missing user is still a plain 404; the photos are then written straight into
     * the response. Synchronous on purpose: a StreamingResponseBody finishes on an async dispatch,
     * which the JWT filter does not authenticate, so the download would end in a 401.
     */
    @GetMapping("/{id}/export")
    public void export(@CurrentUserId UUID adminId, @PathVariable UUID id, HttpServletResponse response)
            throws IOException {
        var pkg = dataExport.collect(id, adminId);
        String filename = "dane-" + pkg.lastName() + "-" + LocalDate.now() + ".zip";
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString());
        dataExport.writeZip(pkg, response.getOutputStream());
    }

    @PostMapping("/{id}/logout-all")
    public ResponseEntity<Void> forceLogout(@PathVariable UUID id) {
        service.forceLogout(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/promote")
    public AdminUserResponse promote(@CurrentUserId UUID adminId, @PathVariable UUID id) {
        return service.promote(adminId, id);
    }

    @PostMapping("/{id}/demote")
    public AdminUserResponse demote(@CurrentUserId UUID adminId, @PathVariable UUID id) {
        return service.demote(adminId, id);
    }

    /** Marks the user as a 1-on-1 coaching client — unlocks the personal training calendar. */
    @PostMapping("/{id}/athlete")
    public AdminUserResponse enableAthlete(@PathVariable UUID id) {
        return service.setAthlete(id, true);
    }

    /** Hides the personal training calendar. Non-destructive — the plan and its history survive. */
    @DeleteMapping("/{id}/athlete")
    public AdminUserResponse disableAthlete(@PathVariable UUID id) {
        return service.setAthlete(id, false);
    }

    /**
     * Erases the 1-on-1 plan for good: trainings, goals, comments, photos and the coach's notes
     * about this person. The opposite of the call above, which hides and keeps.
     * <p>
     * The account itself is untouched. Works even when the athlete flag is already gone; that
     * account is the one whose data would otherwise be stranded.
     */
    @DeleteMapping("/{id}/training-plan")
    public AdminTrainingPlanErasureService.ErasedPlan eraseTrainingPlan(@CurrentUserId UUID adminId,
                                                                        @PathVariable UUID id) {
        return planErasure.erase(id, adminId);
    }
}
