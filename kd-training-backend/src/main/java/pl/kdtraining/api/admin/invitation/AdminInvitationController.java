package pl.kdtraining.api.admin.invitation;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.kdtraining.api.admin.invitation.InvitationDtos.InvitationResponse;
import pl.kdtraining.api.admin.invitation.InvitationDtos.InviteRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/invitations")
public class AdminInvitationController {

    private final InvitationService service;

    public AdminInvitationController(InvitationService service) {
        this.service = service;
    }

    @GetMapping
    public List<InvitationResponse> list() {
        return service.listOpen();
    }

    /** Also the "send again" action: a second invitation to the same address replaces the first. */
    @PostMapping
    public InvitationResponse invite(@Valid @RequestBody InviteRequest request) {
        return service.invite(request.email());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        service.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
