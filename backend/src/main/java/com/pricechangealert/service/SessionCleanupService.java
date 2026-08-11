package com.pricechangealert.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SessionCleanupService {

    private final AuthService authService;

    public SessionCleanupService(AuthService authService) {
        this.authService = authService;
    }

    @Scheduled(cron = "0 17 3 * * *")
    public void deleteExpiredSessions() {
        authService.deleteExpiredSessions();
    }
}
