package io.github.thegoyalyogesh.qa.parabank.model;

/** Outcome of registering one customer; {@code user} carries final credentials and ids. */
public record RegistrationResult(ParaBankUser user, RegistrationStatus status, String acknowledgement) {
}
