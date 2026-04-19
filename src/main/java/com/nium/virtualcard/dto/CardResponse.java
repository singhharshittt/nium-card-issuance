package com.nium.virtualcard.dto;

import com.nium.virtualcard.model.CardStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for card details.
 * Returned by card creation and retrieval endpoints.
 */
@Data
@Builder
public class CardResponse {
    /**
     * Unique identifier of the card.
     */
    private UUID id;

    /**
     * Name of the cardholder.
     */
    private String cardholderName;

    /**
     * Current balance of the card.
     */
    private BigDecimal balance;

    /**
     * Current status of the card.
     */
    private CardStatus status;

    /**
     * Timestamp when the card was created.
     */
    private Instant createdAt;
}
