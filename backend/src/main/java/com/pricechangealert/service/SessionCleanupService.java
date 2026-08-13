package com.pricechangealert.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SessionCleanupService {

    private final AuthService authService;
    private final AuthTokenService authTokens;
    private final PasskeyService passkeys;

    public SessionCleanupService(AuthService authService, AuthTokenService authTokens,
                                 PasskeyService passkeys) {
        this.authService = authService;
        this.authTokens = authTokens;
        this.passkeys = passkeys;
    }

    @Scheduled(cron = "0 17 3 * * *")
    public void deleteExpiredSessions() {
        authService.deleteExpiredSessions();
        authTokens.deleteExpired();
        passkeys.deleteExpiredChallenges();
    }
}
