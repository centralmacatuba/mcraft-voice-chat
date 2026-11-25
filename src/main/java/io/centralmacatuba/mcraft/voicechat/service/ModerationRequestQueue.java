package io.centralmacatuba.mcraft.voicechat.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Serializes moderation mutations per player so backend requests cannot complete out of order.
 * A failed operation does not prevent the next queued mutation from being attempted.
 */
public final class ModerationRequestQueue {
    private final ConcurrentMap<UUID, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();

    public CompletableFuture<Void> submit(UUID playerId, Supplier<CompletableFuture<Void>> request) {
        AtomicReference<CompletableFuture<Void>> result = new AtomicReference<>();
        tails.compute(playerId, (id, previous) -> {
            CompletableFuture<Void> start = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.handle((ignored, error) -> null);
            CompletableFuture<Void> operation = start.thenCompose(ignored -> {
                try {
                    CompletableFuture<Void> pending = request.get();
                    return pending == null
                            ? CompletableFuture.failedFuture(new IllegalStateException("Moderation request returned no future."))
                            : pending;
                } catch (RuntimeException exception) {
                    return CompletableFuture.failedFuture(exception);
                }
            });
            result.set(operation);
            CompletableFuture<Void> tail = operation.handle((ignored, error) -> null);
            // Async cleanup avoids recursively mutating this map from inside compute() when a request
            // completes synchronously (for example, a cached/mock provider in tests).
            tail.whenCompleteAsync((ignored, error) -> tails.remove(id, tail));
            return tail;
        });
        return result.get();
    }
}
