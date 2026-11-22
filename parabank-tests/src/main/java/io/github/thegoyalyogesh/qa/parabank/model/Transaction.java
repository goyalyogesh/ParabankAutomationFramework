package io.github.thegoyalyogesh.qa.parabank.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One account transaction from the REST API; {@code type} is "Credit" or "Debit". {@code date} is the booking date:
 * the API sends it as epoch milliseconds at midnight UTC, so it is read in UTC.
 */
public record Transaction(long id, long accountId, String type, LocalDate date, BigDecimal amount, String description) {
}
