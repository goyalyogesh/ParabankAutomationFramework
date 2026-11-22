package io.github.thegoyalyogesh.qa.parabank.model;

/** {@code GET /customers/{customerId}} (address flattened). */
public record Customer(long id, String firstName, String lastName, String street, String city, String state,
                       String zipCode, String phone, String ssn) {
}
