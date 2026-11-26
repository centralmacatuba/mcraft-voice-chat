package io.centralmacatuba.mcraft.voicechat.service;

import org.junit.jupiter.api.Test;

import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BoundedHttpBodySubscriberTest {
    @Test
    void deadlineCancelsStalledBodySubscriptionAndFailsTheBodyFuture() {
        BoundedHttpBodySubscriber subscriber = new BoundedHttpBodySubscriber(1024, Duration.ofMillis(50));
        AtomicBoolean cancelled = new AtomicBoolean();
        subscriber.onSubscribe(subscription(cancelled));

        CompletionException failure = assertThrows(CompletionException.class,
                () -> subscriber.getBody().toCompletableFuture().orTimeout(2, TimeUnit.SECONDS).join());

        assertTrue(rootCause(failure) instanceof HttpTimeoutException);
        assertTrue(cancelled.get(), "deadline must cancel the publisher subscription");
    }

    @Test
    void exceedingTheLimitCancelsTheSubscription() {
        BoundedHttpBodySubscriber subscriber = new BoundedHttpBodySubscriber(4, Duration.ofSeconds(2));
        AtomicBoolean cancelled = new AtomicBoolean();
        subscriber.onSubscribe(subscription(cancelled));

        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[5])));

        assertThrows(CompletionException.class, () -> subscriber.getBody().toCompletableFuture().join());
        assertTrue(cancelled.get());
    }

    @Test
    void completesWithBoundedBodyWhenPublisherFinishes() {
        BoundedHttpBodySubscriber subscriber = new BoundedHttpBodySubscriber(8, Duration.ofSeconds(2));
        subscriber.onSubscribe(subscription(new AtomicBoolean()));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2, 3})));
        subscriber.onComplete();

        assertArrayEquals(new byte[]{1, 2, 3}, subscriber.getBody().toCompletableFuture().join());
    }

    private static Flow.Subscription subscription(AtomicBoolean cancelled) {
        return new Flow.Subscription() {
            @Override public void request(long count) { }
            @Override public void cancel() { cancelled.set(true); }
        };
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }
}
