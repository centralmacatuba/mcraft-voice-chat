package io.centralmacatuba.mcraft.voicechat.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Executable model of the backend compare-and-set contract.
 * This is deliberately a contract fixture, not an implementation of the external backend.
 */
class ModerationRevisionContractTest {
    @Test
    void lateOlderMutationCannotOverwriteNewerAcceptedRevision() {
        ContractStore store = new ContractStore();
        UUID player = UUID.randomUUID();
        UUID newerOperation = UUID.randomUUID();

        store.apply(player, 22L, newerOperation, false);

        assertThrows(StaleRevisionException.class,
                () -> store.apply(player, 21L, UUID.randomUUID(), true));
        assertEquals(new State(22L, newerOperation, false), store.state(player));
    }

    @Test
    void retryingIdenticalOperationIsIdempotent() {
        ContractStore store = new ContractStore();
        UUID player = UUID.randomUUID();
        UUID operation = UUID.randomUUID();

        State first = store.apply(player, 7L, operation, true);
        State retry = store.apply(player, 7L, operation, true);

        assertSame(first, retry);
        assertEquals(1, store.acceptedMutationCount());
    }

    @Test
    void operationIdCannotBeReusedWithDifferentPayload() {
        ContractStore store = new ContractStore();
        UUID player = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        store.apply(player, 7L, operation, true);

        assertThrows(IllegalArgumentException.class,
                () -> store.apply(player, 7L, operation, false));
    }

    private record State(long revision, UUID operationId, boolean muted) { }

    private static final class ContractStore {
        private final Map<UUID, State> states = new HashMap<>();
        private final Map<UUID, State> operations = new HashMap<>();
        private int acceptedMutationCount;

        synchronized State apply(UUID player, long revision, UUID operationId, boolean muted) {
            State previousOperation = operations.get(operationId);
            if (previousOperation != null) {
                if (previousOperation.revision() == revision && previousOperation.muted() == muted
                        && states.containsKey(player)) {
                    return previousOperation;
                }
                throw new IllegalArgumentException("Idempotency key reused with a different payload.");
            }

            State current = states.getOrDefault(player, new State(0L, new UUID(0L, 0L), false));
            if (revision <= current.revision()) throw new StaleRevisionException();

            State accepted = new State(revision, operationId, muted);
            states.put(player, accepted);
            operations.put(operationId, accepted);
            acceptedMutationCount++;
            return accepted;
        }

        State state(UUID player) {
            return states.get(player);
        }

        int acceptedMutationCount() {
            return acceptedMutationCount;
        }
    }

    private static final class StaleRevisionException extends RuntimeException { }
}
