package io.github.thegoyalyogesh.qa.parabank.model;

/** One row of the LoginNegative sheet, with {U01}-style placeholders already resolved. */
public record LoginCase(String caseId, String description, String username, String password, String expectedError) {
}
