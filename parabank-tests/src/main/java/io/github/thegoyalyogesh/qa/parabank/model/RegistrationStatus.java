package io.github.thegoyalyogesh.qa.parabank.model;

public enum RegistrationStatus {
    /** Registered by this run through the UI. */
    REGISTERED,
    /** Credentials from Excel already work (checked through the REST API), so registration was skipped. */
    EXISTING,
    /** Registration scenario failed. */
    FAILED
}
