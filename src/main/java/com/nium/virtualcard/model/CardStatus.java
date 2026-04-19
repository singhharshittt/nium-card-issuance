package com.nium.virtualcard.model;

/**
 * Enumeration of possible card statuses.
 */
public enum CardStatus {
    /**
     * Card is active and can be used for top-ups and spends.
     */
    ACTIVE,

    /**
     * Card is blocked and cannot be used for any financial operations.
     */
    BLOCKED,

    /**
     * Card is closed and no longer available.
     */
    CLOSED;

    /**
     * Parse a string value to CardStatus enum.
     *
     * @param value the string value (case-insensitive)
     * @return the corresponding CardStatus enum value
     * @throws IllegalArgumentException if the value does not match any enum constant
     */
    public static CardStatus fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("CardStatus value cannot be null");
        }
        return CardStatus.valueOf(value.toUpperCase());
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

