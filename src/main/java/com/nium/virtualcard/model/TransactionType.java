package com.nium.virtualcard.model;

/**
 * Enumeration of transaction types.
 */
public enum TransactionType {
    /**
     * Initial card issuance transaction when a card is created with an initial balance.
     */
    CARD_ISSUANCE,

    /**
     * Top-up transaction to add funds to a card.
     */
    TOP_UP,

    /**
     * Spend transaction to deduct funds from a card.
     */
    SPEND;

    /**
     * Parse a string value to TransactionType enum.
     *
     * @param value the string value (case-insensitive)
     * @return the corresponding TransactionType enum value
     * @throws IllegalArgumentException if the value does not match any enum constant
     */
    public static TransactionType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("TransactionType value cannot be null");
        }
        return TransactionType.valueOf(value.toUpperCase());
    }

    /**
     * Get the string representation of this enum value.
     *
     * @return the string representation in uppercase
     */
    public String getValue() {
        return this.name();
    }
}

