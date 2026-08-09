package com.pricechangealert.web;

import com.pricechangealert.service.PushService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/push")
public class PushController {

    public record SubscribeRequest(
            @NotBlank String endpoint,
            @NotBlank String p256dh,
            @NotBlank String auth) {
    }

    private final PushService pushService;

    public PushController(PushService pushService) {
        this.pushService = pushService;
    }

    @GetMapping("/vapid-key")
    public Map<String, String> vapidKey() {
        try {
            return Map.of("publicKey", pushService.vapidPublicKey());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    @PostMapping("/subscribe")
    public Map<String, Boolean> subscribe(@RequestHeader(value = "X-Visitor-Id", required = false) String visitorId,
                                          @Valid @RequestBody SubscribeRequest req) {
        pushService.saveSubscription(VisitorId.normalize(visitorId), req.endpoint(), req.p256dh(), req.auth());
        return Map.of("ok", true);
    }

    @DeleteMapping({ "/subscribe", "/unsubscribe" })
    public Map<String, Boolean> unsubscribe(@RequestHeader(value = "X-Visitor-Id", required = false) String visitorId,
                                            @RequestBody Map<String, String> body) {
        pushService.removeSubscription(VisitorId.normalize(visitorId), body.getOrDefault("endpoint", ""));
        return Map.of("ok", true);
    }
}

