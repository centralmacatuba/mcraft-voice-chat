package io.centralmacatuba.mcraft.voicechat.service;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/** Deterministic HTTP boundary stub for provider contract tests. */
final class StubHttpClient extends HttpClient {
    private final int statusCode;
    private final Object responseBody;
    private final Throwable failure;
    private final boolean stallBody;
    private final AtomicBoolean bodyCancelled = new AtomicBoolean();
    private HttpRequest lastRequest;
    private String lastRequestBody;
    private int callCount;

    StubHttpClient(int statusCode, Object responseBody) {
        this(statusCode, responseBody, null, false);
    }

    StubHttpClient(int statusCode, Object responseBody, boolean stallBody) {
        this(statusCode, responseBody, null, stallBody);
    }

    StubHttpClient(Throwable failure) {
        this(0, null, failure, false);
    }

    private StubHttpClient(int statusCode, Object responseBody, Throwable failure, boolean stallBody) {
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.failure = failure;
        this.stallBody = stallBody;
    }

    boolean bodyCancelled() {
        return bodyCancelled.get();
    }

    HttpRequest lastRequest() {
        return lastRequest;
    }

    int callCount() {
        return callCount;
    }

    String lastRequestBody() {
        return lastRequestBody;
    }

    @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
    @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
    @Override public Redirect followRedirects() { return Redirect.NEVER; }
    @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
    @Override public SSLContext sslContext() { return null; }
    @Override public SSLParameters sslParameters() { return new SSLParameters(); }
    @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
    @Override public Version version() { return Version.HTTP_1_1; }
    @Override public Optional<Executor> executor() { return Optional.empty(); }
    @Override public WebSocket.Builder newWebSocketBuilder() { throw new UnsupportedOperationException(); }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
        throw new UnsupportedOperationException("Tests use asynchronous provider requests.");
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        lastRequest = request;
        callCount++;
        captureRequestBody(request);
        if (failure != null) return CompletableFuture.failedFuture(failure);
        HttpResponse.ResponseInfo info = new HttpResponse.ResponseInfo() {
            @Override public int statusCode() { return statusCode; }
            @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
            @Override public Version version() { return Version.HTTP_1_1; }
        };
        HttpResponse.BodySubscriber<T> subscriber = responseBodyHandler.apply(info);
        subscriber.onSubscribe(new Flow.Subscription() {
            private boolean cancelled;
            @Override public void request(long count) {
                if (cancelled || count <= 0 || stallBody) return;
                try {
                    if (responseBody instanceof java.io.InputStream stream) {
                        byte[] data = stream.readAllBytes();
                        if (data.length > 0) subscriber.onNext(List.of(ByteBuffer.wrap(data)));
                    }
                    subscriber.onComplete();
                } catch (IOException exception) {
                    subscriber.onError(exception);
                }
            }
            @Override public void cancel() {
                cancelled = true;
                bodyCancelled.set(true);
            }
        });
        return subscriber.getBody().toCompletableFuture().thenApply(body ->
                new StubHttpResponse<>(request, statusCode, body));
    }

    private void captureRequestBody(HttpRequest request) {
        request.bodyPublisher().ifPresent(publisher -> {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            publisher.subscribe(new Flow.Subscriber<>() {
                @Override public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }
                @Override public void onNext(ByteBuffer item) {
                    byte[] bytes = new byte[item.remaining()];
                    item.get(bytes);
                    output.write(bytes, 0, bytes.length);
                }
                @Override public void onError(Throwable error) {
                    throw new AssertionError("Could not capture test request body.", error);
                }
                @Override public void onComplete() {
                    lastRequestBody = output.toString(StandardCharsets.UTF_8);
                }
            });
        });
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return sendAsync(request, responseBodyHandler);
    }

    private record StubHttpResponse<T>(HttpRequest request, int statusCode, T body) implements HttpResponse<T> {
        @Override public int statusCode() { return statusCode; }
        @Override public HttpRequest request() { return request; }
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
        @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public Version version() { return Version.HTTP_1_1; }
    }
}
