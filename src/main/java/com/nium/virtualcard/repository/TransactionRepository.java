package com.nium.virtualcard.repository;

import com.nium.virtualcard.model.Transaction;
import com.nium.virtualcard.model.TransactionStatus;
import com.nium.virtualcard.model.TransactionType;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.*;

/**
 * Repository for Transaction persistence operations using jOOQ.
 *
 * Provides data access for transaction operations including idempotency checking,
 * transaction history retrieval, and ledger management.
 */
@Repository
public class TransactionRepository {

    private final DSLContext dsl;

    /**
     * Table and field definitions for the transactions table.
     * Field names must match the database column names exactly (case-sensitive).
     */
    private static final Table<?> TRANSACTIONS = table("transactions");
    private static final Field<UUID> TRANSACTION_ID = field("ID", UUID.class);
    private static final Field<UUID> CARD_ID = field("CARD_ID", UUID.class);
    private static final Field<String> TYPE = field("TYPE", String.class);
    private static final Field<BigDecimal> AMOUNT = field("AMOUNT", BigDecimal.class);
    private static final Field<String> TRANSACTION_STATUS = field("STATUS", String.class);
    private static final Field<String> IDEMPOTENCY_KEY = field("IDEMPOTENCY_KEY", String.class);
    private static final Field<Timestamp> CREATED_AT = field("CREATED_AT", Timestamp.class);
    private static final Field<String> FAILURE_REASON = field("FAILURE_REASON", String.class);

    /**
     * Constructor with DSLContext injection.
     *
     * @param dsl the jOOQ DSLContext for executing queries
     */
    public TransactionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Create a new transaction.
     *
     * @param transaction the Transaction object to insert
     * @return the created Transaction
     */
    @Transactional
    public Transaction createTransaction(Transaction transaction) {
        dsl.insertInto(TRANSACTIONS,
                TRANSACTION_ID, CARD_ID, TYPE, AMOUNT, TRANSACTION_STATUS,
                IDEMPOTENCY_KEY, CREATED_AT, FAILURE_REASON)
                .values(
                        transaction.getId(),
                        transaction.getCardId(),
                        transaction.getType().getValue(),
                        transaction.getAmount(),
                        transaction.getStatus().getValue(),
                        transaction.getIdempotencyKey(),
                        java.sql.Timestamp.from(transaction.getCreatedAt()),
                        transaction.getFailureReason()
                )
                .execute();

        return transaction;
    }

    /**
     * Find all transactions for a specific card, ordered by creation time descending.
     *
     * @param cardId the UUID of the card
     * @return a list of Transaction objects for the card, ordered by creation time (newest first)
     */
    @Transactional(readOnly = true)
    public List<Transaction> findTransactionsByCardId(UUID cardId) {
        return dsl.selectFrom(TRANSACTIONS)
                .where(CARD_ID.eq(cardId))
                .orderBy(CREATED_AT.desc())
                .fetch()
                .map(this::mapToTransaction);
    }

    /**
     * Find an existing transaction by card ID, transaction type, and idempotency key.
     *
     * This is used to detect idempotent retries of the same operation.
     *
     * @param cardId the UUID of the card
     * @param transactionType the type of transaction (TOP_UP or SPEND)
     * @param idempotencyKey the idempotency key to search for
     * @return an Optional containing the matching Transaction, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<Transaction> findExistingTransaction(UUID cardId, TransactionType transactionType, String idempotencyKey) {
        // If idempotency key is null, return empty (no idempotency check needed)
        if (idempotencyKey == null) {
            return Optional.empty();
        }

        Record record = dsl.selectFrom(TRANSACTIONS)
                .where(CARD_ID.eq(cardId))
                .and(TYPE.eq(transactionType.getValue()))
                .and(IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOne();

        if (record == null) {
            return Optional.empty();
        }

        return Optional.of(mapToTransaction(record));
    }

    /**
     * Map a database record to a Transaction domain object.
     *
     * @param record the database record
     * @return the mapped Transaction object
     */
    private Transaction mapToTransaction(Record record) {

        return Transaction.builder()
                .id(record.get(TRANSACTION_ID))
                .cardId(record.get(CARD_ID))
                .type(TransactionType.valueOf(record.get(TYPE)))
                .amount(record.get(AMOUNT))
                .status(TransactionStatus.valueOf(record.get(TRANSACTION_STATUS)))
                .idempotencyKey(record.get(IDEMPOTENCY_KEY))
                .createdAt(record.get(CREATED_AT).toInstant())
                .failureReason(record.get(FAILURE_REASON))
                .build();
    }
}
