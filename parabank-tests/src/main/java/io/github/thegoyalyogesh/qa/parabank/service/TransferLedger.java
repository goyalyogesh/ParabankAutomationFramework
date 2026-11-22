package io.github.thegoyalyogesh.qa.parabank.service;

import io.github.thegoyalyogesh.qa.parabank.model.TransferInstruction;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Expected balance effects of a set of transfers, computed with Streams. */
public final class TransferLedger {

    private TransferLedger() {
    }

    /**
     * Net change per account if every transfer succeeds: each transfer is split into (sender, -amount) and
     * (receiver, +amount), then grouped by account and summed. Sorted by account number (TreeMap).
     */
    public static Map<Long, BigDecimal> expectedNetChange(Collection<TransferInstruction> transfers) {
        return transfers.stream()
                .flatMap(t -> Stream.of(
                        Map.entry(t.fromAccount(), t.amount().negate()),
                        Map.entry(t.toAccount(), t.amount())))
                .collect(Collectors.groupingBy(Map.Entry::getKey, TreeMap::new,
                        Collectors.reducing(BigDecimal.ZERO, Map.Entry::getValue, BigDecimal::add)));
    }
}
