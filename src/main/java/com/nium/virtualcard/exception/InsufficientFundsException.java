package com.nium.virtualcard.exception;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Exception thrown when a spend operation fails due to insufficient card balance.
 */
public class InsufficientFundsException extends RuntimeException {
    /**
     * The ID of the card with insufficient funds.
     */
    private final UUID cardId;

    /**
     * The current balance of the card.
     */
    private final BigDecimal currentBalance;

    /**
     * The amount requested for the operation.
     */
    private final BigDecimal requestedAmount;

    /**
     * Constructor with balance and amount information.
     *
     * @param cardId the UUID of the card
     * @param currentBalance the current balance of the card
     * @param requestedAmount the amount requested for the operation
     */
    public InsufficientFundsException(UUID cardId, BigDecimal currentBalance, BigDecimal requestedAmount) {
        super("Insufficient funds for card " + cardId + ": current balance = " + currentBalance + 
              ", requested amount = " + requestedAmount);
        this.cardId = cardId;
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }

    /**
     * Constructor with custom message.
     *
     * @param message the error message
     */
    public InsufficientFundsException(String message) {
        super(message);
        this.cardId = null;
        this.currentBalance = null;
        this.requestedAmount = null;
    }

    // Getters

    public UUID getCardId() {
        return cardId;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }
}

