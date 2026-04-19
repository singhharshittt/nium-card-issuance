package com.nium.virtualcard.exception;

import com.nium.virtualcard.model.CardStatus;
import java.util.UUID;

/**
 * Exception thrown when a card operation fails due to invalid card state.
 * For example, attempting to spend from a blocked or closed card.
 */
public class InvalidCardStateException extends RuntimeException {
    /**
     * The ID of the card with invalid state.
     */
    private final UUID cardId;

    /**
     * The current status of the card.
     */
    private final CardStatus currentStatus;

    /**
     * The expected/required status for the operation.
     */
    private final CardStatus expectedStatus;

    /**
     * Constructor with card ID and status information.
     *
     * @param cardId the UUID of the card
     * @param currentStatus the current status of the card
     * @param expectedStatus the status required for the operation
     */
    public InvalidCardStateException(UUID cardId, CardStatus currentStatus, CardStatus expectedStatus) {
        super("Card " + cardId + " has invalid state: " + currentStatus + " (expected: " + expectedStatus + ")");
        this.cardId = cardId;
        this.currentStatus = currentStatus;
        this.expectedStatus = expectedStatus;
    }

    /**
     * Constructor with custom message.
     *
     * @param message the error message
     */
    public InvalidCardStateException(String message) {
        super(message);
        this.cardId = null;
        this.currentStatus = null;
        this.expectedStatus = null;
    }

    // Getters

    public UUID getCardId() {
        return cardId;
    }

    public CardStatus getCurrentStatus() {
        return currentStatus;
    }

    public CardStatus getExpectedStatus() {
        return expectedStatus;
    }
}

