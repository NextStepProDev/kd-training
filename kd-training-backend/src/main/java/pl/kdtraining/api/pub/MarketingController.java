package pl.kdtraining.api.pub;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.kdtraining.config.AppConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/public/marketing")
public class MarketingController {

    private final MarketingService service;
    private final AppConfig appConfig;

    public MarketingController(MarketingService service, AppConfig appConfig) {
        this.service = service;
        this.appConfig = appConfig;
    }

    public record UnsubscribeRequest(@NotBlank String token) {}

    // Unsubscribe from marketing e-mails straight from the link in the e-mail (without logging in).
    // Idempotent and enumeration-safe — always 204, regardless of the account's existence/state.
    // Used by the SPA page behind the footer link, which sends the token as JSON.
    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@RequestBody UnsubscribeRequest request) {
        service.unsubscribe(request.token());
        return ResponseEntity.noContent().build();
    }

    /**
     * The same unsubscribe, entered the way a mailbox enters it (RFC 8058 one-click).
     *
     * <p>Gmail and Yahoo draw their own "Unsubscribe" button from the {@code List-Unsubscribe} headers and
     * then POST to the address in them by themselves. That request looks nothing like the one the SPA sends:
     * no JavaScript runs, so the token has to travel <strong>in the URL</strong> — the mailbox has nowhere to
     * get a JSON body from — and the body it does send is the fixed form field
     * {@code List-Unsubscribe=One-Click}.
     *
     * <p>Matched on the presence of {@code token} alone, deliberately without a {@code consumes} condition.
     * Pinning it to {@code application/x-www-form-urlencoded} would send any provider that omits or varies
     * the content type to the JSON handler above, which answers 415 — so nobody gets unsubscribed and the
     * provider records a failed unsubscribe. A mailbox trying to unsubscribe somebody must never be answered
     * with an error. The SPA is unaffected: it sends no {@code token} parameter, so it still lands above.
     *
     * <p>This is one operation with two ways in, not a second implementation — both call the same service
     * method, so the rules about consent, idempotency and not leaking whether an account exists hold once.
     */
    @PostMapping(value = "/unsubscribe", params = "token")
    public ResponseEntity<Void> unsubscribeOneClick(@RequestParam String token) {
        service.unsubscribe(token);
        return ResponseEntity.noContent().build();
    }

    /**
     * A human who opened the header address in a browser, sent to the page meant for them.
     *
     * <p>Mail clients from before one-click (Thunderbird among them) surface the {@code List-Unsubscribe}
     * address as an ordinary link. Left unhandled that is a 405 in the face of somebody who wanted out —
     * and somebody who wanted out and failed presses "report spam", which is the outcome the headers exist
     * to avoid.
     *
     * <p>It redirects and <strong>does not unsubscribe anyone</strong>. That is the whole reason unsubscribing
     * is a POST: mail scanners and link prefetchers follow GETs on their own, and a GET that revoked consent
     * would opt people out who never clicked anything. The token is echoed into our own URL and encoded, so
     * it cannot bend the redirect somewhere else.
     */
    @GetMapping(value = "/unsubscribe", params = "token")
    public ResponseEntity<Void> unsubscribePage(@RequestParam String token) {
        String target = appConfig.getSiteUrl() + "/wypisz-sie?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }
}
