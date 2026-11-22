package io.github.thegoyalyogesh.qa.parabank.model;

/**
 * ParaBank account types. The REST API takes the type as a number (its ordinal: 0, 1, 2), although the spec's
 * description lists the names. Any other number makes the server fail with HTTP 500 (a defect, see guide 12).
 */
public enum AccountType {
    CHECKING, SAVINGS, LOAN;

    public int code() {
        return ordinal();
    }
}
