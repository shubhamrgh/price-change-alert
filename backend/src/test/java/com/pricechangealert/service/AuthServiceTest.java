package com.pricechangealert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pricechangealert.model.UserAccount;
import com.pricechangealert.model.UserSession;
import com.pricechangealert.repository.UserAccountRepository;
import com.pricechangealert.repository.UserSessionRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthServiceTest {

    @Test
    void registrationHashesPasswordAndIssuesHttpOnlySession() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        UserSessionRepository sessions = mock(UserSessionRepository.class);
        LegacyOwnershipMigrationService legacyOwnership = mock(LegacyOwnershipMigrationService.class);
        when(users.existsByEmailIgnoreCase("person@example.com")).thenReturn(false);
        AuthService service = new AuthService(users, sessions, legacyOwnership, Duration.ofDays(30));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        UserAccount account = service.register(" Person@Example.com ", "safe-password", "visitor-token",
                request, response);

        assertEquals("person@example.com", account.getEmail());
        assertNotEquals("safe-password", account.getPasswordHash());
        assertTrue(account.getPasswordHash().startsWith("$2"));
        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie.contains("pca_session="));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Strict"));
        assertFalse(setCookie.contains("safe-password"));

        ArgumentCaptor<UserSession> session = ArgumentCaptor.forClass(UserSession.class);
        verify(sessions).save(session.capture());
        assertEquals(account.getId(), session.getValue().getUserId());
        assertEquals(64, session.getValue().getTokenHash().length());
        verify(users).save(any(UserAccount.class));
        verify(legacyOwnership).claim("visitor-token", account.getId());
    }
}
