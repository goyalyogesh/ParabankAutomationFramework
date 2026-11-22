package io.github.thegoyalyogesh.qa.parabank.model;

/** Kind of row in the Transfers sheet. */
public enum TransferType {
    /** UI: open a savings account, then move money into it. */
    OWN,
    /** UI: pay another customer's account through Bill Pay. */
    BILLPAY,
    /** REST API: one link of a chain of transfers between different customers, run in sheet order. */
    CHAIN
}
