package io.centralmacatuba.mcraft.voicechat.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

    public HttpVoiceTokenProvider(URI endpoint, String bearerToken, Duration requestTimeout) {
        if (!"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new IllegalArgumentException("The token-provider endpoint must use HTTPS.");
        }
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new IllegalArgumentException("The token-provider bearer token is missing.");
        }
        this.endpoint = endpoint;
        this.bearerToken = bearerToken;
        this.requestTimeout = requestTimeout;
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
        if ("wss".equals(scheme) && host != null) {
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

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            throw new IllegalStateException("Missing string field: " + key);
        }
        String value = json.get(key).getAsString();
        if (value.isBlank()) {
            throw new IllegalStateException("Empty string field: " + key);
        }
        return value;
    }
}
