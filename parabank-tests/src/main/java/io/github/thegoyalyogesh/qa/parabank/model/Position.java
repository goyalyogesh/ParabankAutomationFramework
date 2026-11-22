package io.github.thegoyalyogesh.qa.parabank.model;

import java.math.BigDecimal;

/** A holding of shares ({@code /positions}, {@code buyPosition}, {@code sellPosition}). */
public record Position(long positionId, long customerId, String name, String symbol, int shares,
                       BigDecimal purchasePrice) {
}
