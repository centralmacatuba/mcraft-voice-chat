package io.centralmacatuba.mcraft.voicechat.service;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;

class HttpVoiceTokenProviderTransportTest {
    private static final UUID PLAYER = UUID.fromString("7a8b9c10-1112-4131-8151-123456789abc");
    private static final String ROOM = "minecraft-survival";

    @Test
    void sendsAuthenticatedRequestAndParsesSuccessfulResponse() {
        StubHttpClient client = response(200, tokenResponse());
        HttpVoiceTokenProvider provider = provider(client);
        VoiceCredentials credentials = provider.createSession(PLAYER, "Player", "server-1", ROOM, false).join();

        assertEquals("wss://livekit.example.net", credentials.url());
        assertEquals(ROOM, credentials.room());
        assertEquals(3, credentials.token().split("\\.", -1).length);
        assertEquals(1, client.callCount());
        assertEquals("POST", client.lastRequest().method());
        assertEquals("Bearer backend-secret", client.lastRequest().headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/json", client.lastRequest().headers().firstValue("Content-Type").orElseThrow());
        assertEquals(Duration.ofSeconds(3), client.lastRequest().timeout().orElseThrow());
    }

    @Test
    void rejectsNonSuccessAndRedirectResponsesWithoutRetryingOrFollowingThem() {
        for (int status : new int[]{302, 401, 500}) {
            StubHttpClient client = response(status, "{}");
            assertThrows(CompletionException.class,
                    () -> provider(client).createSession(PLAYER, "Player", "server", ROOM, false).join());
            assertEquals(1, client.callCount());
        }
    }

    @Test
    void propagatesConnectionFailuresAndTimeouts() {
        for (Throwable failure : new Throwable[]{new java.io.IOException("offline"),
                new HttpTimeoutException("timed out")}) {
            StubHttpClient client = new StubHttpClient(failure);
            assertThrows(CompletionException.class,
                    () -> provider(client).createSession(PLAYER, "Player", "server", ROOM, false).join());
            assertEquals(1, client.callCount());
        }
    }

    @Test
    void rejectsUnexpectedRoomAndOversizedResponse() {
        StubHttpClient wrongRoom = response(200, tokenResponse().replace(ROOM, "other-room"));
        assertThrows(CompletionException.class,
                () -> provider(wrongRoom).createSession(PLAYER, "Player", "server", ROOM, false).join());

        StubHttpClient oversized = response(200, " ".repeat(65 * 1024));
        assertThrows(CompletionException.class,
                () -> provider(oversized).createSession(PLAYER, "Player", "server", ROOM, false).join());
    }

    private static HttpVoiceTokenProvider provider(StubHttpClient client) {
        return new HttpVoiceTokenProvider(URI.create("https://backend.example/token"), "backend-secret",
                Duration.ofSeconds(3), Duration.ofMinutes(10), false, client);
    }

    private static StubHttpClient response(int status, String body) {
        return new StubHttpClient(status, new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String tokenResponse() {
        JsonObject response = new JsonObject();
        response.addProperty("url", "wss://livekit.example.net");
        response.addProperty("room", ROOM);
        response.addProperty("token", validJwt());
        return response.toString();
    }

    private static String validJwt() {
        JsonObject header = new JsonObject();
        header.addProperty("alg", "HS256");
        JsonObject video = new JsonObject();
        video.addProperty("room", ROOM);
        video.addProperty("roomJoin", true);
        video.addProperty("canPublish", true);
        video.addProperty("canSubscribe", true);
        JsonObject claims = new JsonObject();
        claims.addProperty("sub", PLAYER.toString());
        claims.addProperty("nbf", Instant.now().minusSeconds(1).getEpochSecond());
        claims.addProperty("exp", Instant.now().plusSeconds(300).getEpochSecond());
        claims.add("video", video);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String h = encoder.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8));
        String p = encoder.encodeToString(claims.toString().getBytes(StandardCharsets.UTF_8));
        return h + "." + p + ".dGVzdC1zaWduYXR1cmU";
    }
}
