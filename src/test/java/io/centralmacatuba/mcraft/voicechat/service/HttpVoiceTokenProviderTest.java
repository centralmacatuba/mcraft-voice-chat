package io.centralmacatuba.mcraft.voicechat.service;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpVoiceTokenProviderTest {
    private static final UUID PLAYER_ID = UUID.fromString("7a8b9c10-1112-4131-8151-123456789abc");
    private static final String ROOM = "minecraft-survival";

    @Test
    void acceptsMatchingShortLivedParticipantToken() {
        String token = token(PLAYER_ID.toString(), ROOM, Instant.now().plusSeconds(300).getEpochSecond(), true);
        assertDoesNotThrow(() -> HttpVoiceTokenProvider.validateTokenClaims(
                token, PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void rejectsTokenForAnotherPlayer() {
        String token = token(UUID.randomUUID().toString(), ROOM,
                Instant.now().plusSeconds(300).getEpochSecond(), true);
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                token, PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void rejectsTokenForAnotherRoom() {
        String token = token(PLAYER_ID.toString(), "other-room",
                Instant.now().plusSeconds(300).getEpochSecond(), true);
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                token, PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void rejectsExpiredToken() {
        String token = token(PLAYER_ID.toString(), ROOM,
                Instant.now().minusSeconds(1).getEpochSecond(), true);
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                token, PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void rejectsTokenWithExcessiveLifetime() {
        String token = token(PLAYER_ID.toString(), ROOM,
                Instant.now().plusSeconds(3600).getEpochSecond(), true);
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                token, PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void rejectsTokenWithoutRoomJoinGrant() {
        String token = token(PLAYER_ID.toString(), ROOM,
                Instant.now().plusSeconds(300).getEpochSecond(), false);
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                token, PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void rejectsMalformedJwt() {
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                "not-a-jwt", PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    private static String token(String identity, String room, long expiresAt, boolean roomJoin) {
        JsonObject header = new JsonObject();
        header.addProperty("alg", "HS256");
        header.addProperty("typ", "JWT");

        JsonObject video = new JsonObject();
        video.addProperty("room", room);
        video.addProperty("roomJoin", roomJoin);

        JsonObject claims = new JsonObject();
        claims.addProperty("sub", identity);
        claims.addProperty("exp", expiresAt);
        claims.add("video", video);

        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String encodedHeader = encoder.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8));
        String encodedClaims = encoder.encodeToString(claims.toString().getBytes(StandardCharsets.UTF_8));
        return encodedHeader + "." + encodedClaims + ".test-signature";
    }
}
