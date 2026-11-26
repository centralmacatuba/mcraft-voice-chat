package io.centralmacatuba.mcraft.voicechat.service;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Collects a small HTTP response without blocking a worker thread.
 * The deadline is measured from construction (before request dispatch), and expiry cancels
 * the publisher subscription as well as the exchange future.
 */
final class BoundedHttpBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private static final ScheduledThreadPoolExecutor TIMER = createTimer();

    private final int maximumBytes;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<?>> exchange = new AtomicReference<>();
    private final AtomicBoolean terminated = new AtomicBoolean();
    private final ScheduledFuture<?> deadline;

    BoundedHttpBodySubscriber(int maximumBytes, Duration timeout) {
        if (maximumBytes < 1 || timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Body limit and timeout must be positive.");
        }
        this.maximumBytes = maximumBytes;
        this.deadline = TIMER.schedule(this::expire, timeout.toNanos(), TimeUnit.NANOSECONDS);
        body.whenComplete((ignored, error) -> deadline.cancel(false));
    }

    private static ScheduledThreadPoolExecutor createTimer() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "mcraft-voice-http-deadline");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    void bindExchange(CompletableFuture<?> request) {
        exchange.set(request);
        if (terminated.get()) request.cancel(true);
    }

    @Override
    public java.util.concurrent.CompletionStage<byte[]> getBody() {
        return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription incoming) {
        if (!subscription.compareAndSet(null, incoming) || terminated.get()) {
            incoming.cancel();
            return;
        }
        incoming.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(List<ByteBuffer> items) {
        if (terminated.get()) return;
        synchronized (bytes) {
            if (terminated.get()) return;
            for (ByteBuffer item : items) {
                int count = item.remaining();
                if (count > maximumBytes - bytes.size()) {
                    fail(new IllegalStateException("HTTP response exceeded the configured size limit."));
                    return;
                }
                byte[] chunk = new byte[count];
                item.get(chunk);
                bytes.write(chunk, 0, chunk.length);
            }
        }
    }

    @Override
    public void onError(Throwable error) {
        fail(error);
    }

    @Override
    public void onComplete() {
        if (!terminated.compareAndSet(false, true)) return;
        synchronized (bytes) {
            body.complete(bytes.toByteArray());
        }
    }

    private void expire() {
        fail(new java.net.http.HttpTimeoutException("HTTP response deadline exceeded."));
    }

    private void fail(Throwable error) {
        if (!terminated.compareAndSet(false, true)) return;
        Flow.Subscription activeSubscription = subscription.get();
        if (activeSubscription != null) activeSubscription.cancel();
        CompletableFuture<?> activeExchange = exchange.get();
        if (activeExchange != null) activeExchange.cancel(true);
        body.completeExceptionally(error);
    }
}
