package com.trailify.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class FaviconControllerTest {

    @Test
    void redirectsLegacyFaviconRequestToSvgFavicon() {
        ResponseEntity<Void> response = new FaviconController().favicon();

        assertEquals(HttpStatus.PERMANENT_REDIRECT, response.getStatusCode());
        assertEquals(URI.create("/favicon.svg"), response.getHeaders().getLocation());
    }
}
