package com.nium.virtualcard.service;

import com.nium.virtualcard.model.Transaction;
import com.nium.virtualcard.model.TransactionStatus;
import com.nium.virtualcard.model.TransactionType;
import com.nium.virtualcard.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for transaction operations.
 *
 * Handles all transaction-related business logic including:
 * - Creating successful and declined transactions
 * - Idempotency checking
 * - Transaction history retrieval
 * - Audit trail management with separate transactions for declined operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;

    /**
     * Create a successful transaction.
     *
     * @param cardId the card ID
     * @param type the transaction type
     * @param amount the transaction amount
     * @param idempotencyKey the idempotency key (can be null for CARD_ISSUANCE)
     * @return the created transaction
     */
    @Transactional
    public Transaction createSuccessfulTransaction(UUID cardId, TransactionType type,
            BigDecimal amount, String idempotencyKey) {
        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .cardId(cardId)
                .type(type)
                .amount(amount)
                .status(TransactionStatus.SUCCESSFUL)
                .idempotencyKey(idempotencyKey)
                .createdAt(Instant.now())
                .failureReason(null)
                .build();

        transactionRepository.createTransaction(transaction);
        log.debug("Created successful transaction: {} for card: {}", transaction.getId(), cardId);

        return transaction;
    }

    /**
     * Create a declined transaction for audit trail purposes.
     *
     * Uses REQUIRES_NEW propagation to ensure the declined transaction is saved
     * even if the main business transaction is rolled back.
     *
     * @param cardId the card ID
     * @param type the transaction type
     * @param amount the transaction amount
     * @param idempotencyKey the idempotency key
     * @param failureReason the reason for decline
     * @return the created declined transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction createDeclinedTransaction(UUID cardId, TransactionType type,
            BigDecimal amount, String idempotencyKey, String failureReason) {
        Transaction declinedTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .cardId(cardId)
                .type(type)
                .amount(amount)
                .status(TransactionStatus.DECLINED)
                .idempotencyKey(idempotencyKey)
                .createdAt(Instant.now())
                .failureReason(failureReason)
                .build();

        transactionRepository.createTransaction(declinedTransaction);
        log.debug("Created declined transaction: {} for card: {} with reason: {}",
                declinedTransaction.getId(), cardId, failureReason);

        return declinedTransaction;
    }

    /**
     * Check if a transaction already exists for the given idempotency key.
     *
     * Used for idempotency validation in financial operations.
     *
     * @param cardId the card ID
     * @param type the transaction type
     * @param idempotencyKey the idempotency key to check
     * @return Optional containing the existing transaction if found
     */
    @Transactional(readOnly = true)
    public Optional<Transaction> findExistingTransaction(UUID cardId, TransactionType type, String idempotencyKey) {
        return transactionRepository.findExistingTransaction(cardId, type, idempotencyKey);
    }

    /**
     * Get the complete transaction history for a card.
     *
     * @param cardId the card ID
     * @return list of transactions ordered by creation time (newest first)
     */
    @Transactional(readOnly = true)
    public List<Transaction> getTransactionHistory(UUID cardId) {
        return transactionRepository.findTransactionsByCardId(cardId);
    }
}
