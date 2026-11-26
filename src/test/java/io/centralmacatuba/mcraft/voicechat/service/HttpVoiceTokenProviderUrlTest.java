package io.centralmacatuba.mcraft.voicechat.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpVoiceTokenProviderUrlTest {
    @Test
    void acceptsSecureWebSocketUrlByDefault() {
        assertDoesNotThrow(() -> HttpVoiceTokenProvider.validateLiveKitUrl("wss://livekit.example.net"));
    }

    @Test
    void rejectsInsecureLoopbackUrlByDefault() {
        assertThrows(IllegalStateException.class,
                () -> HttpVoiceTokenProvider.validateLiveKitUrl("ws://127.0.0.1:7880"));
    }

    @Test
    void acceptsInsecureLoopbackUrlOnlyWithExplicitOptIn() {
        assertDoesNotThrow(() ->
                HttpVoiceTokenProvider.validateLiveKitUrl("ws://localhost:7880", true));
    }

    @Test
    void rejectsRemoteInsecureUrlEvenWithLoopbackOptIn() {
        assertThrows(IllegalStateException.class,
                () -> HttpVoiceTokenProvider.validateLiveKitUrl("ws://livekit.example.net", true));
    }

    @Test
    void rejectsUrlWithUserInformation() {
        assertThrows(IllegalStateException.class,
                () -> HttpVoiceTokenProvider.validateLiveKitUrl("wss://user:password@livekit.example.net"));
    }

    @Test
    void rejectsUrlWithFragment() {
        assertThrows(IllegalStateException.class,
                () -> HttpVoiceTokenProvider.validateLiveKitUrl("wss://livekit.example.net/#fragment"));
    }
}
