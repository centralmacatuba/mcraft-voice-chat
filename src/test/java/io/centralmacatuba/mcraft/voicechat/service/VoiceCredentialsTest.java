package io.centralmacatuba.mcraft.voicechat.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceCredentialsTest {
    @Test
    void redactsTokenFromToString() {
        VoiceCredentials credentials = new VoiceCredentials(
                "wss://livekit.example.net", "test-room", "sensitive-livekit-jwt");
        assertFalse(credentials.toString().contains("sensitive-livekit-jwt"));
        assertTrue(credentials.toString().contains("[REDACTED]"));
    }
}
