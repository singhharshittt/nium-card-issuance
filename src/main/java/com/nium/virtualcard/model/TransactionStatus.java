package com.nium.virtualcard.model;

/**
 * Enumeration of transaction statuses.
 */
public enum TransactionStatus {
    /**
     * Transaction was processed successfully.
     */
    SUCCESSFUL,

    /**
     * Transaction was declined (e.g., insufficient funds).
     */
    DECLINED,

    /**
     * Transaction is pending (for future asynchronous processing).
     */
    PENDING;

    /**
     * Parse a string value to TransactionStatus enum.
     *
     * @param value the string value (case-insensitive)
     * @return the corresponding TransactionStatus enum value
     * @throws IllegalArgumentException if the value does not match any enum constant
     */
    public static TransactionStatus fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("TransactionStatus value cannot be null");
        }
        return TransactionStatus.valueOf(value.toUpperCase());
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

