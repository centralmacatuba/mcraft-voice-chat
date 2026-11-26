package io.centralmacatuba.mcraft.voicechat.service;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;

class HttpVoiceModerationProviderTest {
    @Test
    void rejectsNonHttpsEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> new HttpVoiceModerationProvider(
                URI.create("http://backend.example/moderation"), "secret", Duration.ofSeconds(5)));
    }

    @Test
    void rejectsEndpointWithUserInformation() {
        assertThrows(IllegalArgumentException.class, () -> new HttpVoiceModerationProvider(
                URI.create("https://" + "fixture-user" + ":" + "fixture-password" + "@backend.example/moderation"),
                "secret", Duration.ofSeconds(5)));
    }

    @Test
    void rejectsBlankBearerToken() {
        assertThrows(IllegalArgumentException.class, () -> new HttpVoiceModerationProvider(
                URI.create("https://backend.example/moderation"), " ", Duration.ofSeconds(5)));
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new HttpVoiceModerationProvider(
                URI.create("https://backend.example/moderation"), "secret", Duration.ZERO));
    }

    @Test
    void sendsAuthenticatedModerationRequestAndAcceptsSuccessfulResponse() {
        StubHttpClient client = new StubHttpClient(204, null);
        HttpVoiceModerationProvider provider = new HttpVoiceModerationProvider(
                URI.create("https://backend.example/moderation"), "backend-secret",
                Duration.ofSeconds(4), client);
        UUID playerId = UUID.randomUUID();

        assertDoesNotThrow(() -> provider.setMuted(playerId, "survival-1", "survival", true).join());
        assertEquals(1, client.callCount());
        assertEquals("POST", client.lastRequest().method());
        assertEquals("Bearer backend-secret", client.lastRequest().headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/json", client.lastRequest().headers().firstValue("Content-Type").orElseThrow());
        assertEquals(Duration.ofSeconds(4), client.lastRequest().timeout().orElseThrow());
        assertEquals(URI.create("https://backend.example/moderation"), client.lastRequest().uri());
    }

    @Test
    void rejectsNonSuccessAndRedirectResponsesWithoutRetryingOrFollowingThem() {
        for (int status : new int[]{302, 401, 500}) {
            StubHttpClient client = new StubHttpClient(status, null);
            HttpVoiceModerationProvider provider = new HttpVoiceModerationProvider(
                    URI.create("https://backend.example/moderation"), "secret",
                    Duration.ofSeconds(2), client);
            assertThrows(CompletionException.class,
                    () -> provider.setMuted(UUID.randomUUID(), "server", "room", true).join());
            assertEquals(1, client.callCount());
        }
    }

    @Test
    void propagatesConnectionFailuresAndTimeouts() {
        for (Throwable failure : new Throwable[]{new java.io.IOException("offline"),
                new HttpTimeoutException("timed out")}) {
            StubHttpClient client = new StubHttpClient(failure);
            HttpVoiceModerationProvider provider = new HttpVoiceModerationProvider(
                    URI.create("https://backend.example/moderation"), "secret",
                    Duration.ofSeconds(2), client);
            assertThrows(CompletionException.class,
                    () -> provider.setMuted(UUID.randomUUID(), "server", "room", false).join());
            assertEquals(1, client.callCount());
        }
    }
}
