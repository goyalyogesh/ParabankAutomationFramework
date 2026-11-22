package io.github.thegoyalyogesh.qa.parabank.model;

import java.util.Map;

/** One row of the Users sheet. Immutable: the {@code with...} methods return changed copies. */
public record ParaBankUser(String userId, String firstName, String lastName, String street, String city,
                           String state, String zipCode, String phone, String ssn,
                           String username, String password, String status,
                           String customerId, String accountNumber) {

    public static ParaBankUser fromRow(Map<String, String> row) {
        return new ParaBankUser(
                value(row, "UserId"), value(row, "FirstName"), value(row, "LastName"),
                value(row, "Street"), value(row, "City"), value(row, "State"), value(row, "ZipCode"),
                value(row, "Phone"), value(row, "SSN"), value(row, "Username"), value(row, "Password"),
                value(row, "Status"), value(row, "CustomerId"), value(row, "AccountNumber"));
    }

    private static String value(Map<String, String> row, String column) {
        String cell = row.get(column);
        return cell == null ? "" : cell.trim();
    }

    public boolean hasCredentials() {
        return !username.isBlank() && !password.isBlank();
    }

    public boolean isRegistered() {
        return RegistrationStatus.REGISTERED.name().equals(status) || RegistrationStatus.EXISTING.name().equals(status);
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public ParaBankUser withCredentials(String newUsername, String newPassword) {
        return new ParaBankUser(userId, firstName, lastName, street, city, state, zipCode, phone, ssn,
                newUsername, newPassword, status, customerId, accountNumber);
    }

    public ParaBankUser withRegistration(String newStatus, String newCustomerId, String newAccountNumber) {
        return new ParaBankUser(userId, firstName, lastName, street, city, state, zipCode, phone, ssn,
                username, password, newStatus, newCustomerId, newAccountNumber);
    }
}
