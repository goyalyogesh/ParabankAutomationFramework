package io.github.thegoyalyogesh.qa.parabank.model;

import java.math.BigDecimal;

/** A transfer with resolved account numbers, ready to execute. */
public record TransferInstruction(String transferId, long fromAccount, long toAccount, BigDecimal amount) {
}
