package com.nium.virtualcard.exception;

import java.util.UUID;

/**
 * Exception thrown when an optimistic locking conflict occurs.
 * This happens when the version of a card in the database has changed since it was last read,
 * indicating concurrent modifications. After retrying, if the conflict persists beyond the retry limit,
 * this exception is thrown to the caller.
 */
public class ConcurrentUpdateException extends RuntimeException {
    /**
     * The ID of the card that had the concurrent update conflict.
     */
    private final UUID cardId;

    /**
     * The expected version that did not match.
     */
    private final Long expectedVersion;

    /**
     * The actual version found in the database.
     */
    private final Long actualVersion;

    /**
     * The number of retries attempted before giving up.
     */
    private final int retriesAttempted;

    /**
     * Constructor with conflict details.
     *
     * @param cardId the UUID of the card
     * @param expectedVersion the version expected by the update
     * @param actualVersion the actual version found in the database
     * @param retriesAttempted the number of retries attempted
     */
    public ConcurrentUpdateException(UUID cardId, Long expectedVersion, Long actualVersion, int retriesAttempted) {
        super("Concurrent update conflict for card " + cardId + ": expected version " + expectedVersion +
              " but found " + actualVersion + " (retries attempted: " + retriesAttempted + ")");
        this.cardId = cardId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
        this.retriesAttempted = retriesAttempted;
    }

    /**
     * Constructor with custom message.
     *
     * @param message the error message
     */
    public ConcurrentUpdateException(String message) {
        super(message);
        this.cardId = null;
        this.expectedVersion = null;
        this.actualVersion = null;
        this.retriesAttempted = 0;
    }

    // Getters

    public UUID getCardId() {
        return cardId;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public Long getActualVersion() {
        return actualVersion;
    }

    public int getRetriesAttempted() {
        return retriesAttempted;
    }
}

