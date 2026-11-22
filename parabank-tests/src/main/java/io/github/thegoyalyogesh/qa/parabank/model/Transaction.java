package io.github.thegoyalyogesh.qa.parabank.model;

import java.math.BigDecimal;

/** One account transaction from the REST API; {@code type} is "Credit" or "Debit". */
public record Transaction(long id, long accountId, String type, BigDecimal amount, String description) {
}
