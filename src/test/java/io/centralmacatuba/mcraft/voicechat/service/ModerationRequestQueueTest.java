package io.centralmacatuba.mcraft.voicechat.service;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class ModerationRequestQueueTest {
    @Test
    void serializesMutationsForSamePlayerInSubmissionOrder() {
        ModerationRequestQueue queue = new ModerationRequestQueue();
        UUID player = UUID.randomUUID();
        CompletableFuture<Void> firstGate = new CompletableFuture<>();
        List<String> calls = new ArrayList<>();

        CompletableFuture<Void> first = queue.submit(player, () -> {
            calls.add("mute");
            return firstGate;
        });
        CompletableFuture<Void> second = queue.submit(player, () -> {
            calls.add("unmute");
            return CompletableFuture.completedFuture(null);
        });

        assertEquals(List.of("mute"), calls);
        assertFalse(second.isDone());
        firstGate.complete(null);
        CompletableFuture.allOf(first, second).join();
        assertEquals(List.of("mute", "unmute"), calls);
    }

    @Test
    void failedMutationDoesNotBlockNextMutation() {
        ModerationRequestQueue queue = new ModerationRequestQueue();
        UUID player = UUID.randomUUID();
        CompletableFuture<Void> failed = queue.submit(player,
                () -> CompletableFuture.failedFuture(new IllegalStateException("backend unavailable")));
        List<String> calls = new ArrayList<>();
        CompletableFuture<Void> next = queue.submit(player, () -> {
            calls.add("next");
            return CompletableFuture.completedFuture(null);
        });

        assertThrows(Exception.class, failed::join);
        next.join();
        assertEquals(List.of("next"), calls);
    }

    @Test
    void differentPlayersCanProceedIndependently() {
        ModerationRequestQueue queue = new ModerationRequestQueue();
        CompletableFuture<Void> gate = new CompletableFuture<>();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        CompletableFuture<Void> first = queue.submit(firstPlayer, () -> gate);
        CompletableFuture<Void> second = queue.submit(secondPlayer, () -> CompletableFuture.completedFuture(null));

        assertTrue(second.isDone());
        assertFalse(first.isDone());
        gate.complete(null);
        first.join();
    }

    @Test
    void acceptsImmediatelyCompletedRequestsWithoutRecursiveMapMutation() {
        ModerationRequestQueue queue = new ModerationRequestQueue();
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 100; i++) {
            queue.submit(player, () -> CompletableFuture.completedFuture(null)).join();
        }
    }
}
