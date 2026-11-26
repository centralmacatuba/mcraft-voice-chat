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
        assertDoesNotThrow(() -> HttpVoiceTokenProvider.validateTokenClaims(
                token(PLAYER_ID.toString(), ROOM, Instant.now().plusSeconds(300).getEpochSecond(),
                        true, true, true), PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void acceptsForceMutedTokenWithoutPublishGrant() {
        assertDoesNotThrow(() -> HttpVoiceTokenProvider.validateTokenClaims(
                token(PLAYER_ID.toString(), ROOM, Instant.now().plusSeconds(300).getEpochSecond(),
                        true, false, true), PLAYER_ID, ROOM, Duration.ofMinutes(15), true));
    }

    @Test
    void rejectsForceMutedTokenThatCanPublish() {
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                token(PLAYER_ID.toString(), ROOM, Instant.now().plusSeconds(300).getEpochSecond(),
                        true, true, true), PLAYER_ID, ROOM, Duration.ofMinutes(15), true));
    }

    @Test
    void rejectsTokenForAnotherPlayer() {
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                token(UUID.randomUUID().toString(), ROOM, Instant.now().plusSeconds(300).getEpochSecond(),
                        true, true, true), PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void rejectsTokenForAnotherRoom() {
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                token(PLAYER_ID.toString(), "other-room", Instant.now().plusSeconds(300).getEpochSecond(),
                        true, true, true), PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void rejectsExpiredToken() {
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                token(PLAYER_ID.toString(), ROOM, Instant.now().minusSeconds(1).getEpochSecond(),
                        true, true, true), PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void rejectsTokenWithExcessiveLifetime() {
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                token(PLAYER_ID.toString(), ROOM, Instant.now().plusSeconds(3600).getEpochSecond(),
                        true, true, true), PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void rejectsTokenWithoutRoomJoinGrant() {
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                token(PLAYER_ID.toString(), ROOM, Instant.now().plusSeconds(300).getEpochSecond(),
                        false, true, true), PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void acceptsBackendEnforcedMuteEvenWhenLocalHintIsFalse() {
        assertDoesNotThrow(() -> HttpVoiceTokenProvider.validateTokenClaims(
                token(PLAYER_ID.toString(), ROOM, Instant.now().plusSeconds(300).getEpochSecond(),
                        true, false, true), PLAYER_ID, ROOM, Duration.ofMinutes(15), false));
    }

    @Test
    void rejectsTokenWithoutNotBeforeClaim() {
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                tokenWithoutNotBefore(), PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void rejectsNotBeforeFartherThanAllowedClockSkew() {
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                tokenWithNotBefore(Instant.now().plusSeconds(120).getEpochSecond()),
                PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void acceptsNotBeforeWithinAllowedClockSkew() {
        assertDoesNotThrow(() -> HttpVoiceTokenProvider.validateTokenClaims(
                tokenWithNotBefore(Instant.now().plusSeconds(30).getEpochSecond()),
                PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void rejectsFractionalExpirationNumericDate() {
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                tokenWithFractionalExpiration(), PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void rejectsTokenWithoutSubscribeGrant() {
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                token(PLAYER_ID.toString(), ROOM, Instant.now().plusSeconds(300).getEpochSecond(),
                        true, true, false), PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void rejectsMalformedJwt() {
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                "not-a-jwt", PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    @Test
    void rejectsUnsupportedJwtAlgorithm() {
        assertThrows(IllegalStateException.class, () -> HttpVoiceTokenProvider.validateTokenClaims(
                tokenWithAlgorithm("none", PLAYER_ID.toString(), ROOM,
                        Instant.now().plusSeconds(300).getEpochSecond(), true, true, true),
                PLAYER_ID, ROOM, Duration.ofMinutes(15)));
    }

    private static String token(String identity, String room, long expiresAt,
                                boolean roomJoin, boolean canPublish, boolean canSubscribe) {
        return tokenWithAlgorithm("HS256", identity, room, expiresAt, roomJoin, canPublish, canSubscribe);
    }

    private static String tokenWithAlgorithm(String algorithm, String identity, String room, long expiresAt,
                                             boolean roomJoin, boolean canPublish, boolean canSubscribe) {
        JsonObject header = new JsonObject();
        header.addProperty("alg", algorithm);
        header.addProperty("typ", "JWT");
        JsonObject video = new JsonObject();
        video.addProperty("room", room);
        video.addProperty("roomJoin", roomJoin);
        video.addProperty("canPublish", canPublish);
        video.addProperty("canSubscribe", canSubscribe);
        JsonObject claims = new JsonObject();
        claims.addProperty("sub", identity);
        claims.addProperty("exp", expiresAt);
        claims.addProperty("nbf", Instant.now().minusSeconds(1).getEpochSecond());
        claims.add("video", video);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String h = encoder.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8));
        String p = encoder.encodeToString(claims.toString().getBytes(StandardCharsets.UTF_8));
        return h + "." + p + ".dGVzdC1zaWduYXR1cmU";
    } 
    private static String tokenWithNotBefore(long notBefore) {
        JsonObject header = new JsonObject();
        header.addProperty("alg", "HS256");
        JsonObject video = new JsonObject();
        video.addProperty("room", ROOM);
        video.addProperty("roomJoin", true);
        video.addProperty("canPublish", true);
        video.addProperty("canSubscribe", true);
        JsonObject claims = new JsonObject();
        claims.addProperty("sub", PLAYER_ID.toString());
        claims.addProperty("exp", Instant.now().plusSeconds(300).getEpochSecond());
        claims.addProperty("nbf", notBefore);
        claims.add("video", video);
        return encodeJwt(header, claims);
    }

    private static String tokenWithFractionalExpiration() {
        JsonObject header = new JsonObject();
        header.addProperty("alg", "HS256");
        JsonObject video = new JsonObject();
        video.addProperty("room", ROOM);
        video.addProperty("roomJoin", true);
        video.addProperty("canPublish", true);
        video.addProperty("canSubscribe", true);
        JsonObject claims = new JsonObject();
        claims.addProperty("sub", PLAYER_ID.toString());
        claims.addProperty("exp", Instant.now().plusSeconds(300).getEpochSecond() + 0.5);
        claims.addProperty("nbf", Instant.now().minusSeconds(1).getEpochSecond());
        claims.add("video", video);
        return encodeJwt(header, claims);
    }

    private static String encodeJwt(JsonObject header, JsonObject claims) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String h = encoder.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8));
        String p = encoder.encodeToString(claims.toString().getBytes(StandardCharsets.UTF_8));
        return h + "." + p + ".dGVzdC1zaWduYXR1cmU";
    }

    private static String tokenWithoutNotBefore() {
        JsonObject header = new JsonObject();
        header.addProperty("alg", "HS256");
        JsonObject video = new JsonObject();
        video.addProperty("room", ROOM);
        video.addProperty("roomJoin", true);
        video.addProperty("canPublish", true);
        video.addProperty("canSubscribe", true);
        JsonObject claims = new JsonObject();
        claims.addProperty("sub", PLAYER_ID.toString());
        claims.addProperty("exp", Instant.now().plusSeconds(300).getEpochSecond());
        claims.add("video", video);
        return encodeJwt(header, claims);
    }

}
