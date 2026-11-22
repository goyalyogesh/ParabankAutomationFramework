package io.github.thegoyalyogesh.qa.parabank.service;

import io.github.thegoyalyogesh.qa.core.data.TestDataGenerator;
import io.github.thegoyalyogesh.qa.parabank.api.ParaBankApi;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationResult;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationStatus;
import io.github.thegoyalyogesh.qa.parabank.pages.RegisterPage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Registers one customer, idempotently:
 * <ol>
 *   <li>Credentials in Excel already work (REST login) → EXISTING, nothing registered.</li>
 *   <li>Blank credentials → generate unique username/password.</li>
 *   <li>Register through the UI; if the username is taken by someone else → new username, one retry.</li>
 *   <li>Look up customer id and first account number through the REST API.</li>
 * </ol>
 */
public final class RegistrationService {

    public static final String EXISTING_ACK = "Already registered (credentials verified through the REST API)";

    private static final Logger LOG = LogManager.getLogger(RegistrationService.class);

    private final RegisterPage page;
    private final ParaBankApi api;
    private final TestDataGenerator generator;
    private final String usernamePrefix;

    public RegistrationService(RegisterPage page, ParaBankApi api, TestDataGenerator generator, String usernamePrefix) {
        this.page = page;
        this.api = api;
        this.generator = generator;
        this.usernamePrefix = usernamePrefix;
    }

    public RegistrationResult register(ParaBankUser user) {
        if (user.hasCredentials()) {
            Optional<Long> existing = api.login(user.username(), user.password());
            if (existing.isPresent()) {
                LOG.info("{} already exists as {} (customer {})", user.userId(), user.username(), existing.get());
                return result(user, RegistrationStatus.EXISTING, EXISTING_ACK, existing.get());
            }
        }

        ParaBankUser candidate = user.hasCredentials()
                ? user
                : user.withCredentials(generator.uniqueUsername(usernamePrefix), generator.password());

        RegisterPage.Outcome outcome = page.open().register(candidate);
        if (outcome == RegisterPage.Outcome.USERNAME_TAKEN) {
            LOG.warn("{}: username {} is taken on the shared site, generating a new one", user.userId(), candidate.username());
            candidate = candidate.withCredentials(generator.uniqueUsername(usernamePrefix), candidate.password());
            outcome = page.open().register(candidate);
        }
        if (outcome != RegisterPage.Outcome.SUCCESS) {
            throw new IllegalStateException("Registration of " + user.userId() + " failed: " + outcome
                    + " " + page.firstFieldError());
        }

        String acknowledgement = page.successMessage();
        String username = candidate.username();
        long customerId = api.login(username, candidate.password()).orElseThrow(() ->
                new IllegalStateException("UI registration succeeded but REST login failed for " + username));
        LOG.info("{} registered as {} (customer {})", user.userId(), username, customerId);
        return result(candidate, RegistrationStatus.REGISTERED, acknowledgement, customerId);
    }

    private RegistrationResult result(ParaBankUser user, RegistrationStatus status, String acknowledgement, long customerId) {
        List<Long> accounts = api.accountIds(customerId);
        String accountNumber = accounts.isEmpty() ? "" : String.valueOf(accounts.get(0));
        ParaBankUser registered = user.withRegistration(status.name(), String.valueOf(customerId), accountNumber);
        return new RegistrationResult(registered, status, acknowledgement);
    }
}
