package pl.kdtraining.api.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.kdtraining.api.auth.AuthDtos.MessageResponse;
import pl.kdtraining.config.CurrentUserId;

import java.util.UUID;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserDtos.UserResponse> getMe(@CurrentUserId UUID userId) {
        return ResponseEntity.ok(userService.getMe(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<UserDtos.UserResponse> updateMe(@CurrentUserId UUID userId, @Valid @RequestBody UserDtos.UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateMe(userId, request));
    }

    @PutMapping("/me/password")
    public ResponseEntity<MessageResponse> changePassword(@CurrentUserId UUID userId, @Valid @RequestBody UserDtos.ChangePasswordRequest request) {
        userService.changePassword(userId, request);
        return ResponseEntity.ok(new MessageResponse("Password changed successfully"));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@CurrentUserId UUID userId, @RequestBody UserDtos.DeleteAccountRequest request) {
        userService.deleteMe(userId, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/marketing")
    public ResponseEntity<UserDtos.UserResponse> updateMarketing(@CurrentUserId UUID userId, @RequestBody UserDtos.UpdateMarketingRequest request) {
        return ResponseEntity.ok(userService.updateMarketing(userId, request));
    }

    @PostMapping("/me/consents")
    public ResponseEntity<UserDtos.UserResponse> submitConsents(@CurrentUserId UUID userId, @RequestBody UserDtos.ConsentsRequest request) {
        return ResponseEntity.ok(userService.submitConsents(userId, request));
    }

    /**
     * Explicit consent to the 1-on-1 calendar's health data (GDPR art. 9(2)(a)). Lives here, next to
     * the other consents, rather than under /my-training — everything there is blocked until this is
     * given (TrainingConsentInterceptor), so the endpoint that lifts the block cannot sit behind it.
     */
    @PostMapping("/me/training-consent")
    public ResponseEntity<UserDtos.UserResponse> grantTrainingConsent(@CurrentUserId UUID userId) {
        return ResponseEntity.ok(userService.grantTrainingConsent(userId));
    }

    @PostMapping("/me/logout-all")
    public ResponseEntity<Void> logoutAllDevices(@CurrentUserId UUID userId) {
        userService.logoutAllDevices(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<UserDtos.UserResponse> uploadAvatar(@CurrentUserId UUID userId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.uploadAvatar(userId, file));
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<UserDtos.UserResponse> deleteAvatar(@CurrentUserId UUID userId) {
        return ResponseEntity.ok(userService.deleteAvatar(userId));
    }
}
