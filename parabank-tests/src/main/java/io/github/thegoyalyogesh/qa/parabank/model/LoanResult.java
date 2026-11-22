package io.github.thegoyalyogesh.qa.parabank.model;

import java.util.Optional;

/** {@code POST /requestLoan}: approved loans get a new LOAN account; denied ones a message key. */
public record LoanResult(boolean approved, String message, Optional<Long> loanAccountId, String provider) {
}
