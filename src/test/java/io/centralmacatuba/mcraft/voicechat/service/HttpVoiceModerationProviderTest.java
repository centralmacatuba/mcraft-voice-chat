package io.centralmacatuba.mcraft.voicechat.service;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpVoiceModerationProviderTest {
    @Test
    void rejectsNonHttpsEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> new HttpVoiceModerationProvider(
                URI.create("http://backend.example/moderation"), "secret", Duration.ofSeconds(5)));
    }

    @Test
    void rejectsEndpointWithUserInformation() {
        assertThrows(IllegalArgumentException.class, () -> new HttpVoiceModerationProvider(
                URI.create("https://user:password@backend.example/moderation"),
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
}
