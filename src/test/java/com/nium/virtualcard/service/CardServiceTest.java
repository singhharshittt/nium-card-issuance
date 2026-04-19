package com.nium.virtualcard.service;

import com.nium.virtualcard.dto.CardResponse;
import com.nium.virtualcard.dto.CreateCardRequest;
import com.nium.virtualcard.dto.SpendRequest;
import com.nium.virtualcard.dto.TopUpRequest;
import com.nium.virtualcard.dto.TransactionResponse;
import com.nium.virtualcard.exception.CardNotFoundException;
import com.nium.virtualcard.exception.ConcurrentUpdateException;
import com.nium.virtualcard.exception.InsufficientFundsException;
import com.nium.virtualcard.exception.InvalidCardStateException;
import com.nium.virtualcard.model.Card;
import com.nium.virtualcard.model.CardStatus;
import com.nium.virtualcard.model.Transaction;
import com.nium.virtualcard.model.TransactionStatus;
import com.nium.virtualcard.model.TransactionType;
import com.nium.virtualcard.repository.CardRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CardService.
 *
 * Tests business logic for card management, financial operations, idempotency, and optimistic locking.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private TransactionService transactionService;

    private MeterRegistry meterRegistry;
    private CardService cardService;

    @BeforeEach
    void setUp() {
        // Create a real MeterRegistry for testing
        meterRegistry = new SimpleMeterRegistry();

        // Create CardService with mocked repositories
        cardService = new CardService(cardRepository, transactionService, meterRegistry);

        // Set maxRetries to a specific value for testing
        cardService.maxRetries = 3;
    }

    // ==================== Card Creation Tests ====================

    @Test
    void testCreateCard_Success() {
        // Arrange
        CreateCardRequest request = CreateCardRequest.builder()
                .cardholderName("John Doe")
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        // Act
        CardResponse response = cardService.createCard(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getCardholderName()).isEqualTo("John Doe");
        assertThat(response.getBalance()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(response.getStatus()).isEqualTo(CardStatus.ACTIVE);
        assertThat(response.getId()).isNotNull();
        assertThat(response.getCreatedAt()).isNotNull();

        // Verify repository calls
        verify(cardRepository, times(1)).createCard(any(Card.class));
        verify(transactionService, times(1)).createSuccessfulTransaction(any(), any(), any(), any());

        // Verify CARD_ISSUANCE transaction was created
        verify(transactionService).createSuccessfulTransaction(
            any(UUID.class), eq(TransactionType.CARD_ISSUANCE), eq(new BigDecimal("1000.00")), eq(null));
    }

    @Test
    void testCreateCard_NegativeBalance_ThrowsException() {
        // Arrange
        CreateCardRequest request = CreateCardRequest.builder()
                .cardholderName("John Doe")
                .initialBalance(new BigDecimal("-100.00"))
                .build();

        // Act & Assert
        assertThatThrownBy(() -> cardService.createCard(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void testCreateCard_TracksMetric() {
        // Arrange
        CreateCardRequest request = CreateCardRequest.builder()
                .cardholderName("Jane Doe")
                .initialBalance(new BigDecimal("500.00"))
                .build();

        // Act
        cardService.createCard(request);

        // Assert
        assertThat(meterRegistry.counter("card.created.count").count()).isEqualTo(1);
    }

    // ==================== Card Retrieval Tests ====================

    @Test
    void testGetCard_Success() {
        // Arrange
        UUID cardId = UUID.randomUUID();
        Card card = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("1000.00"))
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .version(0L)
                .build();
        when(cardRepository.findCardById(cardId)).thenReturn(Optional.of(card));

        // Act
        CardResponse response = cardService.getCard(cardId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(cardId);
        assertThat(response.getCardholderName()).isEqualTo("John Doe");
        assertThat(response.getBalance()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(response.getStatus()).isEqualTo(CardStatus.ACTIVE);
    }

    @Test
    void testGetCard_CardNotFound_ThrowsException() {
        // Arrange
        UUID cardId = UUID.randomUUID();
        when(cardRepository.findCardById(cardId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> cardService.getCard(cardId))
                .isInstanceOf(CardNotFoundException.class);
    }

    // ==================== Top-up Tests ====================

    @Test
    void testTopUp_Success() {
        // Arrange
        UUID cardId = UUID.randomUUID();
        String idempotencyKey = "top-up-key-1";
        Card card = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("1000.00"))
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .version(0L)
                .build();
        TopUpRequest request = TopUpRequest.builder()
                .amount(new BigDecimal("500.00"))
                .build();
        Card updatedCard = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("1500.00"))
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .version(1L)
                .build();

        // Use lenient stubbing to allow multiple calls
        doReturn(Optional.of(card), Optional.of(updatedCard)).when(cardRepository).findCardById(cardId);
        doReturn(Optional.empty()).when(transactionService).findExistingTransaction(cardId, TransactionType.TOP_UP, idempotencyKey);
        doReturn(true).when(cardRepository).updateCardBalanceIfVersionMatches(any(), any(), any());

        // Act
        CardResponse response = cardService.topUp(cardId, request, idempotencyKey);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getBalance()).isEqualTo(new BigDecimal("1500.00"));

        // Verify transaction was created
        verify(transactionService).createSuccessfulTransaction(
            eq(cardId), eq(TransactionType.TOP_UP), eq(new BigDecimal("500.00")), eq(idempotencyKey));

        // Verify metrics
        assertThat(meterRegistry.counter("card.topup.success.count").count()).isEqualTo(1);
    }

    @Test
    void testTopUp_NonActiveCard_ThrowsException() {
        // Arrange
        UUID cardId = UUID.randomUUID();
        String idempotencyKey = "top-up-key-2";
        Card card = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("1000.00"))
                .status(CardStatus.BLOCKED)
                .createdAt(Instant.now())
                .version(0L)
                .build();
        TopUpRequest request = TopUpRequest.builder()
                .amount(new BigDecimal("500.00"))
                .build();

        when(cardRepository.findCardById(cardId)).thenReturn(Optional.of(card));
        when(transactionService.findExistingTransaction(cardId, TransactionType.TOP_UP, idempotencyKey))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> cardService.topUp(cardId, request, idempotencyKey))
                .isInstanceOf(InvalidCardStateException.class);
    }

    @Test
    void testTopUp_MissingIdempotencyKey_ThrowsException() {
        // Arrange
        UUID cardId = UUID.randomUUID();
        TopUpRequest request = TopUpRequest.builder()
                .amount(new BigDecimal("500.00"))
                .build();

        // Act & Assert
        assertThatThrownBy(() -> cardService.topUp(cardId, request, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key");
    }

    @Test
    void testTopUp_IdempotentRetry_ReturnsPriorResult() {
        // Arrange
        UUID cardId = UUID.randomUUID();
        String idempotencyKey = "top-up-key-3";
        TopUpRequest request = TopUpRequest.builder()
                .amount(new BigDecimal("500.00"))
                .build();
        Card card = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("1500.00"))
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .version(1L)
                .build();
        Transaction priorTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .cardId(cardId)
                .type(TransactionType.TOP_UP)
                .amount(new BigDecimal("500.00"))
                .status(TransactionStatus.SUCCESSFUL)
                .idempotencyKey(idempotencyKey)
                .createdAt(Instant.now())
                .failureReason(null)
                .build();

        when(cardRepository.findCardById(cardId)).thenReturn(Optional.of(card));
        when(transactionService.findExistingTransaction(cardId, TransactionType.TOP_UP, idempotencyKey))
                .thenReturn(Optional.of(priorTransaction));

        // Act
        CardResponse response = cardService.topUp(cardId, request, idempotencyKey);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getBalance()).isEqualTo(new BigDecimal("1500.00"));

        // Verify balance update was NOT called (idempotent retry)
        verify(cardRepository, never()).updateCardBalanceIfVersionMatches(any(), any(), any());

        // Verify idempotency hit metric
        assertThat(meterRegistry.counter("idempotency.hit.count").count()).isEqualTo(1);
    }

    @Test
    void testTopUp_OptimisticLockingRetry_Success() {
        // Arrange
        UUID cardId = UUID.randomUUID();
        String idempotencyKey = "top-up-key-4";
        Card card1 = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("1000.00"))
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .version(0L)
                .build();
        Card card2 = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("1200.00"))
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .version(1L)
                .build();
        Card card3 = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("1500.00"))
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .version(2L)
                .build();
        TopUpRequest request = TopUpRequest.builder()
                .amount(new BigDecimal("500.00"))
                .build();

        when(transactionService.findExistingTransaction(cardId, TransactionType.TOP_UP, idempotencyKey))
                .thenReturn(Optional.empty());

        // First call returns card with version 0, update fails (version mismatch)
        when(cardRepository.findCardById(cardId))
                .thenReturn(Optional.of(card1))      // Initial load
                .thenReturn(Optional.of(card2))      // After first retry
                .thenReturn(Optional.of(card3));     // After update succeeds

        when(cardRepository.updateCardBalanceIfVersionMatches(eq(cardId), eq(new BigDecimal("1500.00")), eq(0L)))
                .thenReturn(false);  // First attempt fails
        when(cardRepository.updateCardBalanceIfVersionMatches(eq(cardId), eq(new BigDecimal("1700.00")), eq(1L)))
                .thenReturn(true);   // Second attempt succeeds

        // Act
        CardResponse response = cardService.topUp(cardId, request, idempotencyKey);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getBalance()).isEqualTo(new BigDecimal("1500.00"));

        // Verify retry occurred
        assertThat(meterRegistry.counter("optimistic.lock.retry.count").count()).isEqualTo(1);
    }

    // ==================== Spend Tests ====================

    @Test
    void testSpend_Success() {
        // Arrange
        UUID cardId = UUID.randomUUID();
        String idempotencyKey = "spend-key-1";
        Card card = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("1000.00"))
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .version(0L)
                .build();
        SpendRequest request = SpendRequest.builder()
                .amount(new BigDecimal("300.00"))
                .build();
        Card updatedCard = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("700.00"))
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .version(1L)
                .build();

        // Use lenient stubbing to allow multiple calls
        doReturn(Optional.of(card), Optional.of(updatedCard)).when(cardRepository).findCardById(cardId);
        doReturn(Optional.empty()).when(transactionService).findExistingTransaction(cardId, TransactionType.SPEND, idempotencyKey);
        doReturn(true).when(cardRepository).updateCardBalanceIfVersionMatches(any(), any(), any());

        // Act
        CardResponse response = cardService.spend(cardId, request, idempotencyKey);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getBalance()).isEqualTo(new BigDecimal("700.00"));

        // Verify transaction was created
        verify(transactionService).createSuccessfulTransaction(
            eq(cardId), eq(TransactionType.SPEND), eq(new BigDecimal("300.00")), eq(idempotencyKey));

        // Verify metrics
        assertThat(meterRegistry.counter("card.spend.success.count").count()).isEqualTo(1);
    }

    @Test
    void testSpend_InsufficientFunds_CreatesDeclinedTransaction() {
        // Arrange
        UUID cardId = UUID.randomUUID();
        String idempotencyKey = "spend-key-2";
        Card card = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("100.00"))
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .version(0L)
                .build();
        SpendRequest request = SpendRequest.builder()
                .amount(new BigDecimal("300.00"))
                .build();

        when(cardRepository.findCardById(cardId)).thenReturn(Optional.of(card));
        when(transactionService.findExistingTransaction(cardId, TransactionType.SPEND, idempotencyKey))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> cardService.spend(cardId, request, idempotencyKey))
                .isInstanceOf(InsufficientFundsException.class);

        // Verify DECLINED transaction was created
        verify(transactionService).createDeclinedTransaction(
            eq(cardId), eq(TransactionType.SPEND), eq(new BigDecimal("300.00")), eq(idempotencyKey), eq("Insufficient funds"));

        // Verify spend declined metric
        assertThat(meterRegistry.counter("card.spend.declined.count").count()).isEqualTo(1);
    }

    @Test
    void testSpend_NonActiveCard_ThrowsException() {
        // Arrange
        UUID cardId = UUID.randomUUID();
        String idempotencyKey = "spend-key-3";
        Card card = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("1000.00"))
                .status(CardStatus.CLOSED)
                .createdAt(Instant.now())
                .version(0L)
                .build();
        SpendRequest request = SpendRequest.builder()
                .amount(new BigDecimal("300.00"))
                .build();

        when(cardRepository.findCardById(cardId)).thenReturn(Optional.of(card));
        when(transactionService.findExistingTransaction(cardId, TransactionType.SPEND, idempotencyKey))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> cardService.spend(cardId, request, idempotencyKey))
                .isInstanceOf(InvalidCardStateException.class);
    }

    @Test
    void testSpend_IdempotentRetry_ReturnsPriorResult() {
        // Arrange
        UUID cardId = UUID.randomUUID();
        String idempotencyKey = "spend-key-4";
        SpendRequest request = SpendRequest.builder()
                .amount(new BigDecimal("300.00"))
                .build();
        Card card = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("700.00"))
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .version(1L)
                .build();
        Transaction priorTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .cardId(cardId)
                .type(TransactionType.SPEND)
                .amount(new BigDecimal("300.00"))
                .status(TransactionStatus.SUCCESSFUL)
                .idempotencyKey(idempotencyKey)
                .createdAt(Instant.now())
                .failureReason(null)
                .build();

        when(cardRepository.findCardById(cardId)).thenReturn(Optional.of(card));
        when(transactionService.findExistingTransaction(cardId, TransactionType.SPEND, idempotencyKey))
                .thenReturn(Optional.of(priorTransaction));

        // Act
        CardResponse response = cardService.spend(cardId, request, idempotencyKey);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getBalance()).isEqualTo(new BigDecimal("700.00"));

        // Verify balance update was NOT called (idempotent retry)
        verify(cardRepository, never()).updateCardBalanceIfVersionMatches(any(), any(), any());

        // Verify idempotency hit metric
        assertThat(meterRegistry.counter("idempotency.hit.count").count()).isEqualTo(1);
    }

    @Test
    void testSpend_BalanceBecomeInsufficient_DuringRetry() {
        // Arrange
        UUID cardId = UUID.randomUUID();
        String idempotencyKey = "spend-key-5";
        Card card1 = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("500.00"))
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .version(0L)
                .build();
        Card card2 = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("200.00"))
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .version(1L)
                .build();
        SpendRequest request = SpendRequest.builder()
                .amount(new BigDecimal("300.00"))
                .build();

        when(transactionService.findExistingTransaction(cardId, TransactionType.SPEND, idempotencyKey))
                .thenReturn(Optional.empty());

        when(cardRepository.findCardById(cardId))
                .thenReturn(Optional.of(card1))      // Initial load
                .thenReturn(Optional.of(card2));     // After first retry

        when(cardRepository.updateCardBalanceIfVersionMatches(eq(cardId), eq(new BigDecimal("200.00")), eq(0L)))
                .thenReturn(false);  // Version mismatch, need to retry

        // Act & Assert
        assertThatThrownBy(() -> cardService.spend(cardId, request, idempotencyKey))
                .isInstanceOf(InsufficientFundsException.class);

        // Verify DECLINED transaction was created due to concurrent spend
        verify(transactionService).createDeclinedTransaction(
            eq(cardId), eq(TransactionType.SPEND), eq(new BigDecimal("300.00")), eq(idempotencyKey), eq("Insufficient funds (concurrent)"));
    }

    @Test
    void testSpend_OptimisticLockingExhausted_ThrowsException() {
        // Arrange
        UUID cardId = UUID.randomUUID();
        String idempotencyKey = "spend-key-6";
        Card card = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("1000.00"))
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .version(0L)
                .build();
        SpendRequest request = SpendRequest.builder()
                .amount(new BigDecimal("300.00"))
                .build();

        when(transactionService.findExistingTransaction(cardId, TransactionType.SPEND, idempotencyKey))
                .thenReturn(Optional.empty());

        when(cardRepository.findCardById(cardId))
                .thenReturn(Optional.of(card));

        // Every update attempt fails
        when(cardRepository.updateCardBalanceIfVersionMatches(any(), any(), any()))
                .thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> cardService.spend(cardId, request, idempotencyKey))
                .isInstanceOf(ConcurrentUpdateException.class);

        // Verify retry attempts
        assertThat(meterRegistry.counter("optimistic.lock.retry.count").count()).isGreaterThan(0);
        assertThat(meterRegistry.counter("optimistic.lock.failure.count").count()).isEqualTo(1);
    }

    // ==================== Transaction History Tests ====================

    @Test
    void testGetTransactions_Success() {
        // Arrange
        UUID cardId = UUID.randomUUID();
        Card card = Card.builder()
                .id(cardId)
                .cardholderName("John Doe")
                .balance(new BigDecimal("1000.00"))
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .version(0L)
                .build();

        Transaction tx1 = Transaction.builder()
                .id(UUID.randomUUID())
                .cardId(cardId)
                .type(TransactionType.CARD_ISSUANCE)
                .amount(new BigDecimal("1000.00"))
                .status(TransactionStatus.SUCCESSFUL)
                .idempotencyKey(null)
                .createdAt(Instant.now())
                .failureReason(null)
                .build();
        Transaction tx2 = Transaction.builder()
                .id(UUID.randomUUID())
                .cardId(cardId)
                .type(TransactionType.TOP_UP)
                .amount(new BigDecimal("500.00"))
                .status(TransactionStatus.SUCCESSFUL)
                .idempotencyKey("key-1")
                .createdAt(Instant.now())
                .failureReason(null)
                .build();

        List<Transaction> transactions = new ArrayList<>();
        transactions.add(tx2);
        transactions.add(tx1);

        when(cardRepository.findCardById(cardId)).thenReturn(Optional.of(card));
        when(transactionService.getTransactionHistory(cardId)).thenReturn(transactions);

        // Act
        List<TransactionResponse> response = cardService.getTransactions(cardId);

        // Assert
        assertThat(response).hasSize(2);
        assertThat(response.get(0).getType()).isEqualTo(TransactionType.TOP_UP);
        assertThat(response.get(1).getType()).isEqualTo(TransactionType.CARD_ISSUANCE);
    }

    @Test
    void testGetTransactions_CardNotFound_ThrowsException() {
        // Arrange
        UUID cardId = UUID.randomUUID();
        when(cardRepository.findCardById(cardId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> cardService.getTransactions(cardId))
                .isInstanceOf(CardNotFoundException.class);
    }
}

