package io.github.thegoyalyogesh.qa.parabank.service;

import io.github.thegoyalyogesh.qa.parabank.model.TransferInstruction;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes transfers strictly first-in, first-out.
 * <p>
 * {@link ArrayDeque}: O(1) add at the tail and remove at the head, no synchronisation overhead (one scenario thread
 * owns the queue). Unlike {@code LinkedList} it allocates no node per element, and unlike {@code Stack} it is not
 * synchronised. If a transfer fails, the queue stops and the transfers not yet started stay in {@link #remaining()}.
 */
public final class TransferQueue {

    /** Something that can move money; {@code ParaBankApi::transfer} fits. */
    @FunctionalInterface
    public interface Gateway {
        String transfer(long fromAccount, long toAccount, BigDecimal amount);
    }

    private final Deque<TransferInstruction> pending = new ArrayDeque<>();

    public TransferQueue add(TransferInstruction transfer) {
        pending.offerLast(transfer);
        return this;
    }

    public int size() {
        return pending.size();
    }

    public List<TransferInstruction> remaining() {
        return List.copyOf(pending);
    }

    /** Runs every queued transfer in order. Returns confirmations by transfer id, in execution order. */
    public Map<String, String> runAll(Gateway gateway) {
        Map<String, String> confirmations = new LinkedHashMap<>();
        TransferInstruction next;
        while ((next = pending.pollFirst()) != null) {
            confirmations.put(next.transferId(), gateway.transfer(next.fromAccount(), next.toAccount(), next.amount()));
        }
        return confirmations;
    }
}
