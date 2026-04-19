package com.nium.virtualcard.exception;

import com.nium.virtualcard.model.TransactionType;
import java.util.UUID;

/**
 * Exception thrown when an idempotency key conflict is detected.
 * This occurs when the same idempotency key is used for different operation parameters,
 * or when attempting to retry an operation that previously failed or was declined.
 */
public class IdempotencyConflictException extends RuntimeException {
    /**
     * The card ID associated with the conflicting operation.
     */
    private final UUID cardId;

    /**
     * The transaction type of the conflicting operation.
     */
    private final TransactionType transactionType;

    /**
     * The idempotency key that caused the conflict.
     */
    private final String idempotencyKey;

    /**
     * Constructor with conflict details.
     *
     * @param cardId the UUID of the card
     * @param transactionType the type of transaction
     * @param idempotencyKey the conflicting idempotency key
     */
    public IdempotencyConflictException(UUID cardId, TransactionType transactionType, String idempotencyKey) {
        super("Idempotency conflict for card " + cardId + " with transaction type " + transactionType + 
              " and idempotency key: " + idempotencyKey);
        this.cardId = cardId;
        this.transactionType = transactionType;
        this.idempotencyKey = idempotencyKey;
    }

    /**
     * Constructor with custom message.
     *
     * @param message the error message
     */
    public IdempotencyConflictException(String message) {
        super(message);
        this.cardId = null;
        this.transactionType = null;
        this.idempotencyKey = null;
    }

    // Getters

    public UUID getCardId() {
        return cardId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}

