package com.nium.virtualcard.service;

import com.nium.virtualcard.dto.CardResponse;
import com.nium.virtualcard.dto.CreateCardRequest;
import com.nium.virtualcard.dto.SpendRequest;
import com.nium.virtualcard.dto.TopUpRequest;
import com.nium.virtualcard.dto.TransactionResponse;
import com.nium.virtualcard.exception.CardNotFoundException;
import com.nium.virtualcard.exception.ConcurrentUpdateException;
import com.nium.virtualcard.exception.IdempotencyConflictException;
import com.nium.virtualcard.exception.InsufficientFundsException;
import com.nium.virtualcard.exception.InvalidCardStateException;
import com.nium.virtualcard.model.Card;
import com.nium.virtualcard.model.CardStatus;
import com.nium.virtualcard.model.Transaction;
import com.nium.virtualcard.model.TransactionType;
import com.nium.virtualcard.repository.CardRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for card operations.
 *
 * Implements all business logic for card management, including:
 * - Card creation with initial CARD_ISSUANCE transaction
 * - Card retrieval
 * - Balance top-ups with idempotency and optimistic locking
 * - Balance spends with idempotency, optimistic locking, and balance validation
 * - Transaction history retrieval
 *
 * All financial operations are transactional and enforce:
 * - Idempotency: same operation retried returns prior result
 * - Optimistic locking: concurrent updates are detected and retried
 * - Balance constraints: balance never goes negative
 * - Card state validation: only ACTIVE cards can perform operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardService {

    private final CardRepository cardRepository;
    private final TransactionService transactionService;
    private final MeterRegistry meterRegistry;

    /**
     * Maximum number of retry attempts for optimistic locking conflicts.
     * Default is 3, can be overridden via configuration.
     * Package-private for testing purposes.
     */
    @Value("${card-service.optimistic-lock-retries:3}")
    protected int maxRetries;

    /**
     * Create a new virtual card with an initial balance.
     *
     * Creates both the card and an initial CARD_ISSUANCE transaction to establish the ledger.
     *
     * @param request the card creation request with cardholder name and initial balance
     * @return the created card as a CardResponse
     * @throws IllegalArgumentException if initial balance is negative
     */
    @Transactional
    public CardResponse createCard(CreateCardRequest request) {
        log.debug("Creating card for cardholder: {}", request.getCardholderName());

        validateCreateCardRequest(request);

        Card card = buildCard(request);
        cardRepository.createCard(card);
        log.info("Card created with id: {}, cardholder: {}, balance: {}",
                card.getId(), card.getCardholderName(), card.getBalance());

        createCardIssuanceTransaction(card);
        meterRegistry.counter("card.created.count").increment();

        return mapCardToResponse(card);
    }

    /**
     * Retrieve card details by card ID.
     *
     * @param cardId the UUID of the card to retrieve
     * @return the card details as a CardResponse
     * @throws CardNotFoundException if the card does not exist
     */
    @Transactional(readOnly = true)
    public CardResponse getCard(UUID cardId) {
        log.debug("Retrieving card: {}", cardId);

        Card card = findCardById(cardId);

        log.debug("Card retrieved: {}", cardId);
        return mapCardToResponse(card);
    }

    /**
     * Add funds to a card (top-up).
     *
     * This operation is idempotent: the same idempotency key always produces the same result.
     * Uses optimistic locking to handle concurrent top-ups safely.
     *
     * @param cardId the UUID of the card
     * @param request the top-up request with amount
     * @param idempotencyKey the idempotency key for this operation (required)
     * @return the card details after the top-up
     * @throws CardNotFoundException if the card does not exist
     * @throws InvalidCardStateException if the card is not ACTIVE
     * @throws IdempotencyConflictException if idempotency key conflicts with a prior operation
     * @throws ConcurrentUpdateException if optimistic locking fails after retries
     */
    @Transactional
    public CardResponse topUp(UUID cardId, TopUpRequest request, String idempotencyKey) {
        log.debug("Top-up requested for card: {}, amount: {}, idempotencyKey: {}",
                cardId, request.getAmount(), idempotencyKey);

        validateTopUpRequest(request, idempotencyKey);

        if (isIdempotentRetry(cardId, TransactionType.TOP_UP, idempotencyKey)) {
            return handleIdempotentTopUp(cardId);
        }

        Card card = findCardById(cardId);
        validateCardForOperation(card, cardId, "top-up");

        Card updatedCard = performBalanceUpdateWithRetry(cardId, card, request.getAmount(), "top-up", idempotencyKey);

        createTopUpTransaction(cardId, request.getAmount(), idempotencyKey);
        handleTopUpSuccess(cardId, updatedCard.getBalance());

        return mapCardToResponse(updatedCard);
    }

    /**
     * Deduct funds from a card (spend).
     *
     * This operation is idempotent: the same idempotency key always produces the same result.
     * Uses optimistic locking to handle concurrent spends safely.
     * Enforces balance constraint: cannot spend more than available balance.
     *
     * @param cardId the UUID of the card
     * @param request the spend request with amount
     * @param idempotencyKey the idempotency key for this operation (required)
     * @return the card details after the spend
     * @throws CardNotFoundException if the card does not exist
     * @throws InvalidCardStateException if the card is not ACTIVE
     * @throws InsufficientFundsException if the spend amount exceeds the card balance
     * @throws IdempotencyConflictException if idempotency key conflicts with a prior operation
     * @throws ConcurrentUpdateException if optimistic locking fails after retries
     */
    @Transactional
    public CardResponse spend(UUID cardId, SpendRequest request, String idempotencyKey) {
        log.debug("Spend requested for card: {}, amount: {}, idempotencyKey: {}",
                cardId, request.getAmount(), idempotencyKey);

        validateSpendRequest(request, idempotencyKey);

        if (isIdempotentRetry(cardId, TransactionType.SPEND, idempotencyKey)) {
            return handleIdempotentSpend(cardId);
        }

        Card card = findCardById(cardId);
        validateCardForOperation(card, cardId, "spend");
        validateSufficientFunds(card, request.getAmount(), cardId, idempotencyKey);

        Card updatedCard = performBalanceUpdateWithRetry(cardId, card, request.getAmount(), "spend", idempotencyKey);

        createSpendTransaction(cardId, request.getAmount(), idempotencyKey);
        handleSpendSuccess(cardId, updatedCard.getBalance());

        return mapCardToResponse(updatedCard);
    }

    /**
     * Retrieve the complete transaction history for a card.
     *
     * Transactions are returned in reverse chronological order (newest first).
     *
     * @param cardId the UUID of the card
     * @return a list of TransactionResponse objects ordered by creation time (newest first)
     * @throws CardNotFoundException if the card does not exist
     */
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactions(UUID cardId) {
        log.debug("Retrieving transaction history for card: {}", cardId);

        // Verify card exists
        findCardById(cardId);

        List<Transaction> transactions = transactionService.getTransactionHistory(cardId);
        log.debug("Retrieved {} transactions for card: {}", transactions.size(), cardId);

        return transactions.stream()
                .map(this::mapTransactionToResponse)
                .collect(Collectors.toList());
    }

    // Private helper methods

    private void validateCreateCardRequest(CreateCardRequest request) {
        if (request.getInitialBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }
    }

    private Card buildCard(CreateCardRequest request) {
        return Card.builder()
                .id(UUID.randomUUID())
                .cardholderName(request.getCardholderName())
                .balance(request.getInitialBalance())
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .version(0L)
                .build();
    }

    private void createCardIssuanceTransaction(Card card) {
        transactionService.createSuccessfulTransaction(card.getId(), TransactionType.CARD_ISSUANCE, card.getBalance(), null);
        log.debug("CARD_ISSUANCE transaction created");
    }

    private Card findCardById(UUID cardId) {
        return cardRepository.findCardById(cardId)
                .orElseThrow(() -> new CardNotFoundException(cardId));
    }

    private void validateTopUpRequest(TopUpRequest request, String idempotencyKey) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Top-up amount must be positive");
        }
        validateIdempotencyKey(idempotencyKey, "top-up");
    }

    private void validateSpendRequest(SpendRequest request, String idempotencyKey) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Spend amount must be positive");
        }
        validateIdempotencyKey(idempotencyKey, "spend");
    }

    private void validateIdempotencyKey(String idempotencyKey, String operation) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Idempotency-Key header is required for " + operation);
        }
    }

    private boolean isIdempotentRetry(UUID cardId, TransactionType type, String idempotencyKey) {
        return transactionService.findExistingTransaction(cardId, type, idempotencyKey).isPresent();
    }

    private CardResponse handleIdempotentTopUp(UUID cardId) {
        log.debug("Idempotent retry detected for top-up");
        meterRegistry.counter("idempotency.hit.count").increment();
        Card card = findCardById(cardId);
        return mapCardToResponse(card);
    }

    private CardResponse handleIdempotentSpend(UUID cardId) {
        log.debug("Idempotent retry detected for spend");
        meterRegistry.counter("idempotency.hit.count").increment();
        Card card = findCardById(cardId);
        return mapCardToResponse(card);
    }

    private void validateCardForOperation(Card card, UUID cardId, String operation) {
        if (card.getStatus() != CardStatus.ACTIVE) {
            log.warn("{} attempted on non-active card {}: status={}", operation, cardId, card.getStatus());
            throw new InvalidCardStateException(cardId, card.getStatus(), CardStatus.ACTIVE);
        }
    }

    private void validateSufficientFunds(Card card, BigDecimal amount, UUID cardId, String idempotencyKey) {
        if (card.getBalance().compareTo(amount) < 0) {
            log.warn("Insufficient funds for spend on card {}: balance={}, requested={}",
                    cardId, card.getBalance(), amount);
            meterRegistry.counter("card.spend.declined.count").increment();
            transactionService.createDeclinedTransaction(cardId, TransactionType.SPEND, amount, idempotencyKey, "Insufficient funds");
            throw new InsufficientFundsException(cardId, card.getBalance(), amount);
        }
    }

    private Card performBalanceUpdateWithRetry(UUID cardId, Card initialCard, BigDecimal amount, String operation, String idempotencyKey) {
        Card currentCard = initialCard;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            var updateAmount = operation.equals("top-up") ? amount : amount.negate();
            var newBalance = currentCard.getBalance().add(updateAmount);

            boolean updateSucceeded = cardRepository.updateCardBalanceIfVersionMatches(
                    cardId, newBalance, currentCard.getVersion());

            if (updateSucceeded) {
                Card updatedCard = findCardById(cardId);
                log.info("{} successful for card: {}, new balance: {}", operation, cardId, updatedCard.getBalance());
                return updatedCard;
            }

            // Version mismatch - retry
            retryCount++;
            log.debug("Optimistic lock conflict on {}, retrying ({}/{})", operation, retryCount, maxRetries);
            meterRegistry.counter("optimistic.lock.retry.count").increment();

            // Reload card with current version
            currentCard = findCardById(cardId);

            // For spend, re-check balance after reload
            if (operation.equals("spend") && currentCard.getBalance().compareTo(amount) < 0) {
                handleInsufficientFundsDuringRetry(cardId, amount, currentCard.getBalance(), idempotencyKey);
            }
        }

        // Retries exhausted
        handleRetryExhaustion(cardId, operation, retryCount);
        return null; // Won't reach here
    }

    private void handleInsufficientFundsDuringRetry(UUID cardId, BigDecimal requestedAmount, BigDecimal currentBalance, String idempotencyKey) {
        log.warn("Balance became insufficient during retry for card {}: balance={}, requested={}",
                cardId, currentBalance, requestedAmount);
        meterRegistry.counter("card.spend.declined.count").increment();
        transactionService.createDeclinedTransaction(cardId, TransactionType.SPEND, requestedAmount, idempotencyKey, "Insufficient funds (concurrent)");
        throw new InsufficientFundsException(cardId, currentBalance, requestedAmount);
    }

    private void handleRetryExhaustion(UUID cardId, String operation, int retryCount) {
        log.error("Concurrent update conflict on {} for card: {}, exhausted {} retries",
                operation, cardId, maxRetries);
        meterRegistry.counter("optimistic.lock.failure.count").increment();
        if (operation.equals("spend")) {
            meterRegistry.counter("card.spend.failure.count").increment();
        } else {
            meterRegistry.counter("card.topup.failure.count").increment();
        }
        throw new ConcurrentUpdateException(cardId, null, null, retryCount);
    }

    private void createTopUpTransaction(UUID cardId, BigDecimal amount, String idempotencyKey) {
        transactionService.createSuccessfulTransaction(cardId, TransactionType.TOP_UP, amount, idempotencyKey);
    }

    private void createSpendTransaction(UUID cardId, BigDecimal amount, String idempotencyKey) {
        transactionService.createSuccessfulTransaction(cardId, TransactionType.SPEND, amount, idempotencyKey);
    }

    private void handleTopUpSuccess(UUID cardId, BigDecimal newBalance) {
        meterRegistry.counter("card.topup.success.count").increment();
    }

    private void handleSpendSuccess(UUID cardId, BigDecimal newBalance) {
        meterRegistry.counter("card.spend.success.count").increment();
    }

    /**
     * Map a Card domain object to a CardResponse DTO.
     */
    private CardResponse mapCardToResponse(Card card) {
        return CardResponse.builder()
                .id(card.getId())
                .cardholderName(card.getCardholderName())
                .balance(card.getBalance())
                .status(card.getStatus())
                .createdAt(card.getCreatedAt())
                .build();
    }

    /**
     * Map a Transaction domain object to a TransactionResponse DTO.
     */
    private TransactionResponse mapTransactionToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .cardId(transaction.getCardId())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .status(transaction.getStatus())
                .idempotencyKey(transaction.getIdempotencyKey())
                .createdAt(transaction.getCreatedAt())
                .failureReason(transaction.getFailureReason())
                .build();
    }
}
