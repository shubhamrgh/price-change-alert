package com.trailify.web;

import com.trailify.service.PushService;
import com.trailify.service.AuthService;
import com.trailify.service.notification.NotificationPreferenceService;
import jakarta.servlet.http.HttpServletRequest;
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

@RestController
@RequestMapping("/api/push")
public class PushController {

    public record SubscribeRequest(
            @NotBlank String endpoint,
            @NotBlank String p256dh,
            @NotBlank String auth) {
    }

    private final PushService pushService;
    private final AuthService authService;
    private final NotificationPreferenceService preferences;

    public PushController(PushService pushService, AuthService authService,
                          NotificationPreferenceService preferences) {
        this.pushService = pushService;
        this.authService = authService;
        this.preferences = preferences;
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
    public Map<String, Boolean> subscribe(HttpServletRequest request,
                                          @Valid @RequestBody SubscribeRequest req) {
        String userId = authService.requireUser(request).getId();
        pushService.saveSubscription(userId, req.endpoint(), req.p256dh(), req.auth());
        preferences.setWebPush(userId, true);
        return Map.of("ok", true);
    }

    @DeleteMapping({ "/subscribe", "/unsubscribe" })
    public Map<String, Boolean> unsubscribe(HttpServletRequest request,
                                            @RequestBody Map<String, String> body) {
        String userId = authService.requireUser(request).getId();
        boolean hasRemainingDevices = pushService.removeSubscription(
                userId, body.getOrDefault("endpoint", ""));
        preferences.setWebPush(userId, hasRemainingDevices);
        return Map.of("ok", true);
    }
}

