package io.centralmacatuba.mcraft.voicechat.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
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
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private final URI endpoint;
    private final String bearerToken;
    private final HttpClient client;
    private final Duration requestTimeout;
    private final Duration maxTokenLifetime;

    public HttpVoiceTokenProvider(URI endpoint, String bearerToken, Duration requestTimeout,
                                  Duration maxTokenLifetime) {
        if (endpoint == null || !"https".equalsIgnoreCase(endpoint.getScheme())
                || endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("The token-provider endpoint must be a valid HTTPS URL.");
        }
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new IllegalArgumentException("The token-provider bearer token is missing.");
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()
                || maxTokenLifetime == null || maxTokenLifetime.isZero() || maxTokenLifetime.isNegative()) {
            throw new IllegalArgumentException("Token-provider timeouts must be positive.");
        }
        this.endpoint = endpoint;
        this.bearerToken = bearerToken;
        this.requestTimeout = requestTimeout;
        this.maxTokenLifetime = maxTokenLifetime;
        this.client = HttpClient.newBuilder().connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public CompletableFuture<VoiceCredentials> createSession(UUID playerUuid, String username,
                                                               String serverId, String room, boolean forceMuted) {
        JsonObject body = new JsonObject();
        body.addProperty("playerUuid", playerUuid.toString());
        body.addProperty("username", username);
        body.addProperty("serverId", serverId);
        body.addProperty("room", room);
        body.addProperty("forceMuted", forceMuted);

        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(requestTimeout)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .header("Cache-Control", "no-store")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> {
                    try (InputStream responseStream = response.body()) {
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            throw new CompletionException(new IllegalStateException(
                                    "Token backend returned HTTP " + response.statusCode()));
                        }
                        byte[] responseBytes = responseStream.readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (responseBytes.length > MAX_RESPONSE_BYTES) {
                            throw new CompletionException(new IllegalStateException(
                                    "Token backend response exceeded the 64 KiB limit."));
                        }
                        JsonObject json = JsonParser.parseString(
                                new String(responseBytes, StandardCharsets.UTF_8)).getAsJsonObject();
                        String url = requiredString(json, "url");
                        String responseRoom = requiredString(json, "room");
                        String token = requiredString(json, "token");
                        if (!room.equals(responseRoom)) {
                            throw new IllegalStateException("Token backend returned an unexpected room.");
                        }
                        validateLiveKitUrl(url);
                        validateTokenClaims(token, playerUuid, room, maxTokenLifetime, forceMuted);
                        return new VoiceCredentials(url, responseRoom, token);
                    } catch (IOException exception) {
                        throw new CompletionException(new IllegalStateException(
                                "Could not read token backend response."));
                    } catch (RuntimeException exception) {
                        if (exception instanceof CompletionException completionException) throw completionException;
                        throw new CompletionException(new IllegalStateException(
                                "Token backend returned an invalid response."));
                    }
                });
    }

    private static void validateLiveKitUrl(String value) {
        URI uri = URI.create(value);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalStateException("LiveKit URL must not contain user information or a fragment.");
        }
        if ("wss".equals(scheme) && host != null) return;
        if ("ws".equals(scheme) && host != null
                && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
                || host.equals("::1") || host.equals("[::1]"))) return;
        throw new IllegalStateException("LiveKit URL must use wss://; ws:// is allowed only for loopback testing.");
    }

    static void validateTokenClaims(String token, UUID expectedIdentity, String expectedRoom, Duration maxLifetime) {
        validateTokenClaims(token, expectedIdentity, expectedRoom, maxLifetime, false);
    }

    static void validateTokenClaims(String token, UUID expectedIdentity, String expectedRoom,
                                    Duration maxLifetime, boolean forceMuted) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new IllegalStateException("LiveKit token is not a signed JWT.");
        }
        final JsonObject header;
        final JsonObject claims;
        try {
            header = decodeObject(parts[0]);
            claims = decodeObject(parts[1]);
            if (Base64.getUrlDecoder().decode(parts[2]).length == 0) {
                throw new IllegalStateException("LiveKit token has an empty signature.");
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("LiveKit token has an invalid JWT encoding.");
        }
        if (!header.has("alg") || !header.get("alg").isJsonPrimitive()
                || !"HS256".equals(header.get("alg").getAsString())) {
            throw new IllegalStateException("LiveKit token must use the HS256 signing algorithm.");
        }
        if (!expectedIdentity.toString().equals(requiredString(claims, "sub"))) {
            throw new IllegalStateException("LiveKit token identity does not match the Minecraft player UUID.");
        }
        if (!claims.has("video") || !claims.get("video").isJsonObject()) {
            throw new IllegalStateException("LiveKit token is missing video grants.");
        }
        JsonObject video = claims.getAsJsonObject("video");
        if (!expectedRoom.equals(requiredString(video, "room"))) {
            throw new IllegalStateException("LiveKit token is restricted to an unexpected room.");
        }
        requireTrue(video, "roomJoin", "LiveKit token does not grant room participation.");
        requireTrue(video, "canSubscribe", "LiveKit token does not grant audio subscriptions.");
        boolean canPublish = requiredBoolean(video, "canPublish");
        if (canPublish == forceMuted) {
            throw new IllegalStateException(forceMuted
                    ? "A force-muted participant token must not grant audio publishing."
                    : "An unmuted participant token must grant audio publishing.");
        }
        if (!claims.has("exp") || !claims.get("exp").isJsonPrimitive()
                || !claims.get("exp").getAsJsonPrimitive().isNumber()) {
            throw new IllegalStateException("LiveKit token is missing an expiration claim.");
        }
        long expiresAt = claims.get("exp").getAsLong();
        long now = Instant.now().getEpochSecond();
        if (expiresAt <= now || expiresAt > now + maxLifetime.toSeconds()) {
            throw new IllegalStateException("LiveKit token is expired or exceeds the configured lifetime.");
        }
    }

    private static JsonObject decodeObject(String value) {
        return JsonParser.parseString(new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static void requireTrue(JsonObject json, String key, String errorMessage) {
        if (!requiredBoolean(json, key)) throw new IllegalStateException(errorMessage);
    }

    private static boolean requiredBoolean(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.get(key).getAsJsonPrimitive().isBoolean()) {
            throw new IllegalStateException("Missing boolean field: " + key);
        }
        return json.get(key).getAsBoolean();
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.get(key).getAsJsonPrimitive().isString()) {
            throw new IllegalStateException("Missing string field: " + key);
        }
        String value = json.get(key).getAsString();
        if (value.isBlank()) throw new IllegalStateException("Empty string field: " + key);
        return value;
    }
}
