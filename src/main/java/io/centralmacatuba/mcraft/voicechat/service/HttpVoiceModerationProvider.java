package io.centralmacatuba.mcraft.voicechat.service;

import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Sends authoritative participant mute changes to the trusted voice backend. */
public final class HttpVoiceModerationProvider implements VoiceModerationProvider {
    private final URI endpoint;
    private final String bearerToken;
    private final HttpClient client;
    private final Duration requestTimeout;

    public HttpVoiceModerationProvider(URI endpoint, String bearerToken, Duration requestTimeout) {
        this(endpoint, bearerToken, requestTimeout, HttpClient.newBuilder().connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    HttpVoiceModerationProvider(URI endpoint, String bearerToken, Duration requestTimeout, HttpClient client) {
        if (endpoint == null || !"https".equalsIgnoreCase(endpoint.getScheme())
                || endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("The moderation endpoint must be a valid HTTPS URL.");
        }
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new IllegalArgumentException("The moderation bearer token is missing.");
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("The moderation request timeout must be positive.");
        }
        this.endpoint = endpoint;
        this.bearerToken = bearerToken;
        this.requestTimeout = requestTimeout;
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public CompletableFuture<Void> setMuted(UUID playerUuid, String serverId, String room, boolean muted) {
        JsonObject body = new JsonObject();
        body.addProperty("playerUuid", playerUuid.toString());
        body.addProperty("serverId", serverId);
        body.addProperty("room", room);
        body.addProperty("muted", muted);
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(requestTimeout)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .header("Cache-Control", "no-store")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CompletionException(new IllegalStateException(
                        "Voice moderation backend returned HTTP " + response.statusCode()));
            }
            return null;
        });
    }
}
