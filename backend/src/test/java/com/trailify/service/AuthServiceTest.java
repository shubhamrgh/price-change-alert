package com.trailify.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trailify.model.UserAccount;
import com.trailify.model.UserSession;
import com.trailify.repository.UserAccountRepository;
import com.trailify.repository.UserSessionRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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

    @Test
    void guestCanClaimTheSameAccountWithoutLosingItsOwnerId() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        UserSessionRepository sessions = mock(UserSessionRepository.class);
        LegacyOwnershipMigrationService legacyOwnership = mock(LegacyOwnershipMigrationService.class);
        AuthService service = new AuthService(users, sessions, legacyOwnership, Duration.ofDays(30));
        MockHttpServletRequest guestRequest = new MockHttpServletRequest();
        MockHttpServletResponse guestResponse = new MockHttpServletResponse();

        UserAccount guest = service.continueAsGuest(guestRequest, guestResponse);

        assertTrue(guest.isGuest());
        assertTrue(guest.getEmail().startsWith("guest-"));
        ArgumentCaptor<UserSession> sessionCaptor = ArgumentCaptor.forClass(UserSession.class);
        verify(sessions).save(sessionCaptor.capture());
        UserSession guestSession = sessionCaptor.getValue();
        when(sessions.findByTokenHashAndExpiresAtAfter(eq(guestSession.getTokenHash()), any(Instant.class)))
                .thenReturn(Optional.of(guestSession));
        when(users.findById(guest.getId())).thenReturn(Optional.of(guest));
        when(users.existsByEmailIgnoreCase("person@example.com")).thenReturn(false);

        MockHttpServletRequest claimRequest = new MockHttpServletRequest();
        String rawGuestToken = guestResponse.getHeader("Set-Cookie").split("[=;]", 3)[1];
        claimRequest.setCookies(new Cookie(AuthService.SESSION_COOKIE,
                rawGuestToken));
        MockHttpServletResponse claimResponse = new MockHttpServletResponse();
        UserAccount claimed = service.claimGuest(" Person@Example.com ", "safe-password",
                claimRequest, claimResponse);

        assertEquals(guest.getId(), claimed.getId());
        assertEquals("person@example.com", claimed.getEmail());
        assertFalse(claimed.isGuest());
        assertTrue(claimed.getPasswordHash().startsWith("$2"));
        verify(sessions).deleteByUserId(guest.getId());
        assertTrue(claimResponse.getHeader("Set-Cookie").contains(AuthService.SESSION_COOKIE + "="));
    }
}
