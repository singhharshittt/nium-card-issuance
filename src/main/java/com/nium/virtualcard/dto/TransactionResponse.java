package com.nium.virtualcard.dto;

import com.nium.virtualcard.model.TransactionStatus;
import com.nium.virtualcard.model.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for transaction details.
 * Returned by transaction history and financial operation endpoints.
 */
@Data
@Builder
public class TransactionResponse {
    /**
     * Unique identifier of the transaction.
     */
    private UUID id;

    /**
     * UUID of the associated card.
     */
    private UUID cardId;

    /**
     * Type of the transaction (CARD_ISSUANCE, TOP_UP, or SPEND).
     */
    private TransactionType type;

    /**
     * Amount involved in the transaction.
     */
    private BigDecimal amount;

    /**
     * Status of the transaction (SUCCESSFUL, DECLINED, or PENDING).
     */
    private TransactionStatus status;

    /**
     * Idempotency key used for the transaction (if applicable).
     */
    private String idempotencyKey;

    /**
     * Timestamp when the transaction was created.
     */
    private Instant createdAt;

    /**
     * Reason for decline, if the transaction was declined.
     */
    private String failureReason;
}
