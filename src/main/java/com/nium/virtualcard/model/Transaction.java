package com.nium.virtualcard.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain model representing a transaction.
 *
 * This model acts as the ledger for all financial activity on a card.
 * It includes complete transaction history with idempotency support and status tracking.
 */
@Data
@Builder
public class Transaction {
    /**
     * Unique identifier for the transaction (UUID).
     */
    private UUID id;

    /**
     * UUID of the card associated with this transaction.
     */
    private UUID cardId;

    /**
     * Type of transaction (CARD_ISSUANCE, TOP_UP, or SPEND).
     */
    private TransactionType type;

    /**
     * Amount involved in the transaction.
     * Always positive, even for spend operations (sign is determined by type).
     */
    private BigDecimal amount;

    /**
     * Status of the transaction (SUCCESSFUL, DECLINED, or PENDING).
     */
    private TransactionStatus status;

    /**
     * Idempotency key for financial operations (top-ups and spends).
     * Used to ensure that identical retries of the same operation create only one transaction.
     * Can be null for CARD_ISSUANCE transactions.
     */
    private String idempotencyKey;

    /**
     * Timestamp when the transaction was created.
     */
    private Instant createdAt;

    /**
     * Reason for decline, if the transaction was declined.
     * Null for successful transactions or transactions that are pending.
     */
    private String failureReason;

    // Business logic methods

    /**
     * Check if the transaction was successful.
     *
     * @return true if the transaction status is SUCCESSFUL, false otherwise
     */
    public boolean isSuccessful() {
        return this.status == TransactionStatus.SUCCESSFUL;
    }

    /**
     * Check if the transaction was declined.
     *
     * @return true if the transaction status is DECLINED, false otherwise
     */
    public boolean isDeclined() {
        return this.status == TransactionStatus.DECLINED;
    }

    /**
     * Check if the transaction is still pending.
     *
     * @return true if the transaction status is PENDING, false otherwise
     */
    public boolean isPending() {
        return this.status == TransactionStatus.PENDING;
    }
}
