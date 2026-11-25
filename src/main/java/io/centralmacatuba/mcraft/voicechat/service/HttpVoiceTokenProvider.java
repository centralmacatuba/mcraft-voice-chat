package io.centralmacatuba.mcraft.voicechat.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Calls a trusted server-side service that signs short-lived LiveKit participant tokens. */
public final class HttpVoiceTokenProvider implements VoiceTokenProvider {
    private final URI endpoint;
    private final String bearerToken;
    private final HttpClient client;
    private final Duration requestTimeout;
    private final Duration maxTokenLifetime;

    public HttpVoiceTokenProvider(URI endpoint, String bearerToken, Duration requestTimeout,
                                  Duration maxTokenLifetime) {
        if (!"https".equalsIgnoreCase(endpoint.getScheme())
                || endpoint.getHost() == null
                || endpoint.getUserInfo() != null
                || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("The token-provider endpoint must be a valid HTTPS URL.");
        }
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new IllegalArgumentException("The token-provider bearer token is missing.");
        }
        if (requestTimeout.isZero() || requestTimeout.isNegative()
                || maxTokenLifetime.isZero() || maxTokenLifetime.isNegative()) {
            throw new IllegalArgumentException("Token-provider timeouts must be positive.");
        }
        this.endpoint = endpoint;
        this.bearerToken = bearerToken;
        this.requestTimeout = requestTimeout;
        this.maxTokenLifetime = maxTokenLifetime;
        this.client = HttpClient.newBuilder().connectTimeout(requestTimeout).build();
    }

    @Override
    public CompletableFuture<VoiceCredentials> createSession(UUID playerUuid, String username,
                                                               String serverId, String room) {
        JsonObject body = new JsonObject();
        body.addProperty("playerUuid", playerUuid.toString());
        body.addProperty("username", username);
        body.addProperty("serverId", serverId);
        body.addProperty("room", room);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new CompletionException(new IllegalStateException(
                                "Token backend returned HTTP " + response.statusCode()));
                    }
                    try {
                        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                        String url = requiredString(json, "url");
                        String responseRoom = requiredString(json, "room");
                        String token = requiredString(json, "token");
                        if (!room.equals(responseRoom)) {
                            throw new IllegalStateException("Token backend returned an unexpected room.");
                        }
                        validateLiveKitUrl(url);
                        validateTokenClaims(token, playerUuid, room, maxTokenLifetime);
                        return new VoiceCredentials(url, responseRoom, token);
                    } catch (RuntimeException exception) {
                        throw new CompletionException(new IllegalStateException(
                                "Token backend returned an invalid response.", exception));
                    }
                });
    }

    private static void validateLiveKitUrl(String value) {
        URI uri = URI.create(value);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        if ("wss".equals(scheme) && host != null && uri.getUserInfo() == null) {
            return;
        }
        if ("ws".equals(scheme) && host != null
                && (host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1")
                || host.equals("::1")
                || host.equals("[::1]"))) {
            return;
        }
        throw new IllegalStateException("LiveKit URL must use wss://; ws:// is allowed only for loopback testing.");
    }

    static void validateTokenClaims(String token, UUID expectedIdentity, String expectedRoom,
                                     Duration maxLifetime) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[1].isBlank() || parts[2].isBlank()) {
            throw new IllegalStateException("LiveKit token is not a signed JWT.");
        }

        final JsonObject claims;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            claims = JsonParser.parseString(new String(decoded, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("LiveKit token has an invalid JWT payload.", exception);
        }

        String identity = requiredString(claims, "sub");
        if (!expectedIdentity.toString().equals(identity)) {
            throw new IllegalStateException("LiveKit token identity does not match the Minecraft player UUID.");
        }

        if (!claims.has("video") || !claims.get("video").isJsonObject()) {
            throw new IllegalStateException("LiveKit token is missing video grants.");
        }
        JsonObject video = claims.getAsJsonObject("video");
        String tokenRoom = requiredString(video, "room");
        if (!expectedRoom.equals(tokenRoom)) {
            throw new IllegalStateException("LiveKit token is restricted to an unexpected room.");
        }
        requireTrue(video, "roomJoin", "LiveKit token does not grant room participation.");
        requireTrue(video, "canPublish", "LiveKit token does not grant audio publishing.");
        requireTrue(video, "canSubscribe", "LiveKit token does not grant audio subscriptions.");

        if (!claims.has("exp") || !claims.get("exp").isJsonPrimitive()
                || !claims.get("exp").getAsJsonPrimitive().isNumber()) {
            throw new IllegalStateException("LiveKit token is missing an expiration claim.");
        }
        long expiresAt = claims.get("exp").getAsLong();
        long now = Instant.now().getEpochSecond();
        long maxExpiresAt = now + maxLifetime.toSeconds();
        if (expiresAt <= now || expiresAt > maxExpiresAt) {
            throw new IllegalStateException("LiveKit token is expired or exceeds the configured lifetime.");
        }
    }

    private static void requireTrue(JsonObject json, String key, String errorMessage) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.get(key).getAsJsonPrimitive().isBoolean()
                || !json.get(key).getAsBoolean()) {
            throw new IllegalStateException(errorMessage);
        }
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.get(key).getAsJsonPrimitive().isString()) {
            throw new IllegalStateException("Missing string field: " + key);
        }
        String value = json.get(key).getAsString();
        if (value.isBlank()) {
            throw new IllegalStateException("Empty string field: " + key);
        }
        return value;
    }
}
