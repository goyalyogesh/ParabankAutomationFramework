package io.github.thegoyalyogesh.qa.parabank.model;

import java.math.BigDecimal;

/** A bank account as returned by the REST API. Money is BigDecimal, never double. */
public record Account(long id, long customerId, String type, BigDecimal balance) {
}
