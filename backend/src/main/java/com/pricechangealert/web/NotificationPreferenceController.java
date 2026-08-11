package com.pricechangealert.web;

import com.pricechangealert.model.NotificationChannel;
import com.pricechangealert.model.UserAccount;
import com.pricechangealert.service.AuthService;
import com.pricechangealert.service.notification.NotificationPreferenceService;
import com.pricechangealert.service.notification.NotificationPreferenceService.ChannelView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification-preferences")
public class NotificationPreferenceController {

    public record UpdateRequest(boolean enabled, @Size(max = 2048) String destination) {
    }

    private final AuthService authService;
    private final NotificationPreferenceService preferences;

    public NotificationPreferenceController(AuthService authService,
                                            NotificationPreferenceService preferences) {
        this.authService = authService;
        this.preferences = preferences;
    }

    @GetMapping
    public List<ChannelView> list(HttpServletRequest request) {
        return preferences.list(authService.requireUser(request));
    }

    @PutMapping("/{channel}")
    public ChannelView update(@PathVariable NotificationChannel channel,
                              @Valid @RequestBody UpdateRequest update,
                              HttpServletRequest request) {
        UserAccount user = authService.requireUser(request);
        return preferences.update(user, channel, update.enabled(), update.destination());
    }
}
