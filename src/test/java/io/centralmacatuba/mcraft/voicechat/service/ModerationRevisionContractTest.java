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
    private record StoredOperation(UUID player, State state) { }

    private static final class ContractStore {
        private final Map<UUID, State> states = new HashMap<>();
        private final Map<UUID, StoredOperation> operations = new HashMap<>();
        private int acceptedMutationCount;

        synchronized State apply(UUID player, long revision, UUID operationId, boolean muted) {
            StoredOperation previousOperation = operations.get(operationId);
            if (previousOperation != null) {
                State previousState = previousOperation.state();
                if (previousOperation.player().equals(player) && previousState.revision() == revision
                        && previousState.muted() == muted) {
                    return previousState;
                }
                throw new IllegalArgumentException("Idempotency key reused with a different payload.");
            }

            State current = states.getOrDefault(player, new State(0L, new UUID(0L, 0L), false));
            if (revision <= current.revision()) throw new StaleRevisionException();

            State accepted = new State(revision, operationId, muted);
            states.put(player, accepted);
            operations.put(operationId, new StoredOperation(player, accepted));
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
