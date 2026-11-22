package io.github.thegoyalyogesh.qa.parabank.model;

import java.math.BigDecimal;

/** One row of the Transfers sheet. {@code toUserId} is blank for OWN transfers. */
public record TransferCase(String transferId, TransferType type, String fromUserId, String toUserId,
                           BigDecimal amount, String description) {
}
