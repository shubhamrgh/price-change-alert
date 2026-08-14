package com.trailify.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trailify.model.AuthToken;
import com.trailify.repository.AuthTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthTokenServiceTest {

    @Test
    void createsHashedTokenAndConsumesItOnlyThroughItsHash() {
        AuthTokenRepository repository = mock(AuthTokenRepository.class);
        AuthTokenService service = new AuthTokenService(repository);
        String raw = service.create("person@example.com", AuthToken.Type.MAGIC_LOGIN,
                Duration.ofMinutes(15));

        ArgumentCaptor<AuthToken> saved = ArgumentCaptor.forClass(AuthToken.class);
        verify(repository).save(saved.capture());
        assertEquals(43, raw.length());
        assertEquals(64, saved.getValue().getTokenHash().length());
        assertTrue(!raw.equals(saved.getValue().getTokenHash()));

        when(repository.findByTokenHashAndTypeAndExpiresAtAfter(
                eq(AuthTokenService.hash(raw)), eq(AuthToken.Type.MAGIC_LOGIN), any(Instant.class)))
                .thenReturn(Optional.of(saved.getValue()));

        assertEquals(Optional.of("person@example.com"),
                service.consume(raw, AuthToken.Type.MAGIC_LOGIN));
        verify(repository).delete(saved.getValue());
    }
}
