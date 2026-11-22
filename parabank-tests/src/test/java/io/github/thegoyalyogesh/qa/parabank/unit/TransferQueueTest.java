package io.github.thegoyalyogesh.qa.parabank.unit;

import io.github.thegoyalyogesh.qa.parabank.model.TransferInstruction;
import io.github.thegoyalyogesh.qa.parabank.service.TransferLedger;
import io.github.thegoyalyogesh.qa.parabank.service.TransferQueue;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TransferQueueTest {

    private static final List<TransferInstruction> CHAIN = List.of(
            new TransferInstruction("C01", 8, 9, new BigDecimal("20")),
            new TransferInstruction("C02", 9, 10, new BigDecimal("15.50")),
            new TransferInstruction("C03", 10, 8, new BigDecimal("5")));

    @Test
    public void runsTransfersInFifoOrderAndEmptiesTheQueue() {
        TransferQueue queue = new TransferQueue();
        CHAIN.forEach(queue::add);
        List<String> calls = new ArrayList<>();

        Map<String, String> confirmations = queue.runAll((from, to, amount) -> {
            calls.add(from + "->" + to);
            return "ok " + amount;
        });

        assertThat(calls).containsExactly("8->9", "9->10", "10->8");
        assertThat(confirmations.keySet()).containsExactly("C01", "C02", "C03");
        assertThat(confirmations.get("C02")).isEqualTo("ok 15.50");
        assertThat(queue.size()).isZero();
    }

    @Test
    public void failureStopsTheQueueAndKeepsTheRestPending() {
        TransferQueue queue = new TransferQueue();
        CHAIN.forEach(queue::add);

        assertThatThrownBy(() -> queue.runAll((from, to, amount) -> {
            if (from == 9) {
                throw new IllegalStateException("bank down");
            }
            return "ok";
        })).hasMessageContaining("bank down");

        assertThat(queue.remaining()).extracting(TransferInstruction::transferId).containsExactly("C03");
    }

    @Test
    public void ledgerNetsOutEveryAccount() {
        Map<Long, BigDecimal> net = TransferLedger.expectedNetChange(CHAIN);

        assertThat(net.keySet()).containsExactly(8L, 9L, 10L);
        assertThat(net.get(8L)).isEqualByComparingTo("-15");
        assertThat(net.get(9L)).isEqualByComparingTo("4.50");
        assertThat(net.get(10L)).isEqualByComparingTo("10.50");
        assertThat(net.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("0");
    }
}
