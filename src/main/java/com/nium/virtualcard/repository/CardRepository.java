package com.nium.virtualcard.repository;

import com.nium.virtualcard.model.Card;
import com.nium.virtualcard.model.CardStatus;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.*;

/**
 * Repository for Card persistence operations using jOOQ.
 *
 * Provides data access for card CRUD operations and balance management with optimistic locking support.
 */
@Repository
public class CardRepository {

    private final DSLContext dsl;

    /**
     * Table and field definitions for the cards table.
     */
    private static final Table<?> CARDS = table("cards");
    private static final Field<UUID> CARD_ID = field("ID", UUID.class);
    private static final Field<String> CARDHOLDER_NAME = field("CARDHOLDER_NAME", String.class);
    private static final Field<BigDecimal> BALANCE = field("BALANCE", BigDecimal.class);
    private static final Field<String> STATUS = field("STATUS", String.class);
    private static final Field<Timestamp> CREATED_AT = field("CREATED_AT", Timestamp.class);
    private static final Field<Long> VERSION = field("VERSION", Long.class);

    /**
     * Constructor with DSLContext injection.
     *
     * @param dsl the jOOQ DSLContext for executing queries
     */
    public CardRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Find a card by its ID.
     *
     * @param cardId the UUID of the card to retrieve
     * @return an Optional containing the card if found, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<Card> findCardById(UUID cardId) {
        Record record = dsl.selectFrom(CARDS)
                .where(CARD_ID.eq(cardId))
                .fetchOne();

        if (record == null) {
            return Optional.empty();
        }

        return Optional.of(mapToCard(record));
    }

    /**
     * Create a new card.
     *
     * @param card the Card object to insert
     * @return the created Card with all fields populated
     */
    @Transactional
    public Card createCard(Card card) {
        dsl.insertInto(CARDS, CARD_ID, CARDHOLDER_NAME, BALANCE, STATUS, CREATED_AT, VERSION)
                .values(card.getId(), card.getCardholderName(), card.getBalance(),
                       card.getStatus().getValue(), Timestamp.from(card.getCreatedAt()), card.getVersion())
                .execute();

        return card;
    }

    /**
     * Update a card's balance with optimistic locking.
     *
     * The update only succeeds if the current version matches the expected version.
     * On success, the version is incremented.
     *
     * @param cardId the UUID of the card
     * @param newBalance the new balance to set
     * @param currentVersion the expected current version
     * @return true if the update succeeded, false if version mismatch (card was modified concurrently)
     */
    @Transactional
    public boolean updateCardBalanceIfVersionMatches(UUID cardId, BigDecimal newBalance, Long currentVersion) {
        int rowsAffected = dsl.update(CARDS)
                .set(BALANCE, newBalance)
                .set(VERSION, currentVersion + 1)
                .where(CARD_ID.eq(cardId))
                .and(VERSION.eq(currentVersion))
                .execute();

        return rowsAffected > 0;
    }

    /**
     * Map a database record to a Card domain object.
     *
     * @param record the database record
     * @return the mapped Card object
     */
    private Card mapToCard(Record record) {

        return Card.builder()
                .id(record.get(CARD_ID))
                .cardholderName(record.get(CARDHOLDER_NAME))
                .balance(record.get(BALANCE))
                .status(CardStatus.valueOf(record.get(STATUS)))
                .createdAt(record.get(CREATED_AT).toInstant())
                .version(record.get(VERSION))
                .build();
    }
}
