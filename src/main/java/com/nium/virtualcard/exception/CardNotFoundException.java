package com.nium.virtualcard.exception;

import java.util.UUID;

/**
 * Exception thrown when a requested card is not found.
 */
public class CardNotFoundException extends RuntimeException {
    /**
     * The ID of the card that was not found.
     */
    private final UUID cardId;

    /**
     * Constructor with card ID.
     *
     * @param cardId the UUID of the card that was not found
     */
    public CardNotFoundException(UUID cardId) {
        super("Card not found: " + cardId);
        this.cardId = cardId;
    }

    /**
     * Constructor with custom message.
     *
     * @param message the error message
     */
    public CardNotFoundException(String message) {
        super(message);
        this.cardId = null;
    }

    /**
     * Get the card ID that was not found.
     *
     * @return the card UUID, or null if not available
     */
    public UUID getCardId() {
        return cardId;
    }
}

