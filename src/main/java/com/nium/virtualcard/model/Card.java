package com.nium.virtualcard.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain model representing a virtual card.
 *
 * This is the aggregate root for card-related operations and balance management.
 * It includes optimistic locking support via the version field to handle concurrent updates safely.
 */
@Data
@Builder
public class Card {
    /**
     * Unique identifier for the card (UUID).
     */
    private UUID id;

    /**
     * Name of the cardholder.
     */
    private String cardholderName;

    /**
     * Current balance of the card in the default currency.
     * Must always be >= 0 (enforced at database and service layer).
     */
    private BigDecimal balance;

    /**
     * Current status of the card (ACTIVE, BLOCKED, or CLOSED).
     */
    private CardStatus status;

    /**
     * Timestamp when the card was created.
     */
    private Instant createdAt;

    /**
     * Version field for optimistic locking.
     * Incremented on each successful update to detect concurrent modifications.
     */
    private Long version;

}
