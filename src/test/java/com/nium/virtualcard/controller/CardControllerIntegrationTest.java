package com.nium.virtualcard.controller;

import com.nium.virtualcard.dto.CardResponse;
import com.nium.virtualcard.dto.CreateCardRequest;
import com.nium.virtualcard.dto.ErrorResponse;
import com.nium.virtualcard.dto.SpendRequest;
import com.nium.virtualcard.dto.TopUpRequest;
import com.nium.virtualcard.model.CardStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for CardController.
 *
 * Tests full end-to-end flows with real database, Flyway migrations, and HTTP layer.
 * Uses TestRestTemplate to make actual HTTP calls to the running application.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CardControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static final String BASE_URL = "/cards";

    // ==================== End-to-End Flow Tests ====================

    @Test
    void testCompleteCardLifecycle() {
        // Create card
        CreateCardRequest createRequest = CreateCardRequest.builder()
                .cardholderName("John Doe")
                .initialBalance(new BigDecimal("1000.00"))
                .build();
        ResponseEntity<CardResponse> createResponse = restTemplate.postForEntity(
                BASE_URL, createRequest, CardResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CardResponse card = createResponse.getBody();
        assertThat(card).isNotNull();
        assertThat(card.getCardholderName()).isEqualTo("John Doe");
        assertThat(card.getBalance()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(card.getStatus()).isEqualTo(CardStatus.ACTIVE);
        assertThat(card.getId()).isNotNull();

        UUID cardId = card.getId();

        // Get card details
        ResponseEntity<CardResponse> getResponse = restTemplate.getForEntity(
                BASE_URL + "/" + cardId, CardResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        CardResponse retrievedCard = getResponse.getBody();
        assertThat(retrievedCard).isNotNull();
        assertThat(retrievedCard.getId()).isEqualTo(card.getId());
        assertThat(retrievedCard.getCardholderName()).isEqualTo(card.getCardholderName());
        assertThat(retrievedCard.getBalance()).isEqualTo(card.getBalance());
        assertThat(retrievedCard.getStatus()).isEqualTo(card.getStatus());

        // Top-up card
        TopUpRequest topUpRequest = TopUpRequest.builder().amount(new BigDecimal("500.00")).build();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "top-up-test-1");
        HttpEntity<TopUpRequest> topUpEntity = new HttpEntity<>(topUpRequest, headers);

        ResponseEntity<CardResponse> topUpResponse = restTemplate.exchange(
                BASE_URL + "/" + cardId + "/top-ups", HttpMethod.POST, topUpEntity, CardResponse.class);

        assertThat(topUpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        CardResponse toppedUpCard = topUpResponse.getBody();
        assertThat(toppedUpCard).isNotNull();
        assertThat(toppedUpCard.getBalance()).isEqualTo(new BigDecimal("1500.00"));

        // Spend from card
        SpendRequest spendRequest = SpendRequest.builder().amount(new BigDecimal("300.00")).build();
        headers.set("Idempotency-Key", "spend-test-1");
        HttpEntity<SpendRequest> spendEntity = new HttpEntity<>(spendRequest, headers);

        ResponseEntity<CardResponse> spendResponse = restTemplate.exchange(
                BASE_URL + "/" + cardId + "/spends", HttpMethod.POST, spendEntity, CardResponse.class);

        assertThat(spendResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        CardResponse spentCard = spendResponse.getBody();
        assertThat(spentCard).isNotNull();
        assertThat(spentCard.getBalance()).isEqualTo(new BigDecimal("1200.00"));

        // Get transaction history
        ResponseEntity<List> transactionsResponse = restTemplate.getForEntity(
                BASE_URL + "/" + cardId + "/transactions", List.class);
        assertThat(transactionsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> transactions = transactionsResponse.getBody();
        assertThat(transactions).hasSize(3);  // CARD_ISSUANCE + TOP_UP + SPEND

        // Verify transaction types (order: newest first)
        assertThat(transactions.get(0).get("type")).isEqualTo("SPEND");
        assertThat(transactions.get(1).get("type")).isEqualTo("TOP_UP");
        assertThat(transactions.get(2).get("type")).isEqualTo("CARD_ISSUANCE");

        // Verify amounts
        assertThat(new BigDecimal(transactions.get(0).get("amount").toString())).isEqualTo(new BigDecimal("300.0"));
        assertThat(new BigDecimal(transactions.get(1).get("amount").toString())).isEqualTo(new BigDecimal("500.0"));
        assertThat(new BigDecimal(transactions.get(2).get("amount").toString())).isEqualTo(new BigDecimal("1000.0"));
    }

    // ==================== Idempotency Tests ====================

    @Test
    void testTopUpIdempotency() {
        // Create card
        CreateCardRequest createRequest = CreateCardRequest.builder()
                .cardholderName("Jane Doe")
                .initialBalance(new BigDecimal("500.00"))
                .build();
        ResponseEntity<CardResponse> createResponse = restTemplate.postForEntity(
                BASE_URL, createRequest, CardResponse.class);
        UUID cardId = createResponse.getBody().getId();

        // First top-up
        TopUpRequest topUpRequest = TopUpRequest.builder().amount(new BigDecimal("200.00")).build();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "idempotent-top-up-1");
        HttpEntity<TopUpRequest> entity = new HttpEntity<>(topUpRequest, headers);

        ResponseEntity<CardResponse> firstResponse = restTemplate.exchange(
                BASE_URL + "/" + cardId + "/top-ups", HttpMethod.POST, entity, CardResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstResponse.getBody().getBalance()).isEqualTo(new BigDecimal("700.00"));

        // Second top-up with same idempotency key (should return same result)
        ResponseEntity<CardResponse> secondResponse = restTemplate.exchange(
                BASE_URL + "/" + cardId + "/top-ups", HttpMethod.POST, entity, CardResponse.class);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getBody().getBalance()).isEqualTo(new BigDecimal("700.00"));

        // Verify only one transaction was created
        ResponseEntity<List> transactionsResponse = restTemplate.getForEntity(
                BASE_URL + "/" + cardId + "/transactions", List.class);
        List<Map<String, Object>> transactions = transactionsResponse.getBody();
        assertThat(transactions).hasSize(2);  // CARD_ISSUANCE + TOP_UP (only one)
    }

    @Test
    void testSpendIdempotency() {
        // Create card
        CreateCardRequest createRequest = CreateCardRequest.builder()
                .cardholderName("Bob Smith")
                .initialBalance(new BigDecimal("1000.00"))
                .build();
        ResponseEntity<CardResponse> createResponse = restTemplate.postForEntity(
                BASE_URL, createRequest, CardResponse.class);
        UUID cardId = createResponse.getBody().getId();

        // First spend
        SpendRequest spendRequest = SpendRequest.builder().amount(new BigDecimal("200.00")).build();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "idempotent-spend-1");
        HttpEntity<SpendRequest> entity = new HttpEntity<>(spendRequest, headers);

        ResponseEntity<CardResponse> firstResponse = restTemplate.exchange(
                BASE_URL + "/" + cardId + "/spends", HttpMethod.POST, entity, CardResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstResponse.getBody().getBalance()).isEqualTo(new BigDecimal("800.00"));

        // Second spend with same idempotency key (should return same result)
        ResponseEntity<CardResponse> secondResponse = restTemplate.exchange(
                BASE_URL + "/" + cardId + "/spends", HttpMethod.POST, entity, CardResponse.class);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getBody().getBalance()).isEqualTo(new BigDecimal("800.00"));

        // Verify only one transaction was created
        ResponseEntity<List> transactionsResponse = restTemplate.getForEntity(
                BASE_URL + "/" + cardId + "/transactions", List.class);
        List<Map<String, Object>> transactions = transactionsResponse.getBody();
        assertThat(transactions).hasSize(2);  // CARD_ISSUANCE + SPEND (only one)
    }

    // ==================== Error Handling Tests ====================

    @Test
    void testGetNonExistentCard_Returns404() {
        UUID nonExistentId = UUID.randomUUID();
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity(
                BASE_URL + "/" + nonExistentId, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Card Not Found");
        assertThat(response.getBody().getStatus()).isEqualTo(404);
    }

    @Test
    void testSpendInsufficientFunds_Returns409() {
        // Create card with low balance
        CreateCardRequest createRequest = CreateCardRequest.builder()
                .cardholderName("Poor User")
                .initialBalance(new BigDecimal("100.00"))
                .build();
        ResponseEntity<CardResponse> createResponse = restTemplate.postForEntity(
                BASE_URL, createRequest, CardResponse.class);
        UUID cardId = createResponse.getBody().getId();

        // Try to spend more than balance
        SpendRequest spendRequest = SpendRequest.builder().amount(new BigDecimal("200.00")).build();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "insufficient-funds-test");
        HttpEntity<SpendRequest> entity = new HttpEntity<>(spendRequest, headers);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                BASE_URL + "/" + cardId + "/spends", HttpMethod.POST, entity, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Insufficient Funds");
        assertThat(response.getBody().getStatus()).isEqualTo(409);
    }

    @Test
    void testTopUpWithoutIdempotencyKey_Returns400() {
        // Create card
        CreateCardRequest createRequest = CreateCardRequest.builder().cardholderName("Test User")
                .initialBalance(new BigDecimal("1000.00")).build();
        ResponseEntity<CardResponse> createResponse = restTemplate.postForEntity(
                BASE_URL, createRequest, CardResponse.class);
        UUID cardId = createResponse.getBody().getId();

        // Try top-up without Idempotency-Key header
        TopUpRequest topUpRequest = TopUpRequest.builder().amount(new BigDecimal("100.00")).build();
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                BASE_URL + "/" + cardId + "/top-ups", topUpRequest, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Missing Request Header");
        assertThat(response.getBody().getStatus()).isEqualTo(400);
    }

    @Test
    void testCreateCardWithNegativeBalance_Returns400() {
        CreateCardRequest createRequest = CreateCardRequest.builder().cardholderName("Invalid User")
                .initialBalance(new BigDecimal("-100.00")).build();
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                BASE_URL, createRequest, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Validation Error");
        assertThat(response.getBody().getStatus()).isEqualTo(400);
    }

    @Test
    void testValidationErrors_Returns400() {
        // Try to create card with empty name and negative balance
        Map<String, Object> invalidRequest = Map.of(
                "cardholderName", "",
                "initialBalance", -100.0
        );

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                BASE_URL, invalidRequest, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Validation Error");
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getDetails()).contains("cardholderName").contains("initialBalance");
    }

    // ==================== Concurrent Operations Tests ====================

    @Test
    void testConcurrentTopUps() throws Exception {
        // Create card
        CreateCardRequest createRequest = CreateCardRequest.builder().cardholderName("Concurrent User")
                .initialBalance(new BigDecimal("1000.00")).build();
        ResponseEntity<CardResponse> createResponse = restTemplate.postForEntity(
                BASE_URL, createRequest, CardResponse.class);
        UUID cardId = createResponse.getBody().getId();

        // Execute 5 concurrent top-ups of $100 each
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<CompletableFuture<ResponseEntity<CardResponse>>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < 5; i++) {
            final int index = i;
            CompletableFuture<ResponseEntity<CardResponse>> future = CompletableFuture.supplyAsync(() -> {
                TopUpRequest topUpRequest = TopUpRequest.builder().amount(new BigDecimal("100.00")).build();
                HttpHeaders headers = new HttpHeaders();
                headers.set("Idempotency-Key", "concurrent-top-up-" + index);
                HttpEntity<TopUpRequest> entity = new HttpEntity<>(topUpRequest, headers);

                return restTemplate.exchange(
                        BASE_URL + "/" + cardId + "/top-ups", HttpMethod.POST, entity, CardResponse.class);
            }, executor);

            futures.add(future);
        }

        // Wait for all operations to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);

        // Verify final balance is correct (1000 + 5 * 100 = 1500)
        ResponseEntity<CardResponse> finalCardResponse = restTemplate.getForEntity(
                BASE_URL + "/" + cardId, CardResponse.class);
        assertThat(finalCardResponse.getBody().getBalance()).isEqualTo(new BigDecimal("1500.00"));

        // Verify all operations succeeded
        futures.forEach(future -> {
            try {
                assertThat(future.get().getStatusCode()).isEqualTo(HttpStatus.OK);
            } catch (Exception e) {
                fail("Concurrent operation failed", e);
            }
        });

        executor.shutdown();
    }

    @Test
    void testConcurrentSpends_OnlyOneShouldPass() throws Exception {
        // Create card with $300 balance
        CreateCardRequest createRequest = CreateCardRequest.builder().cardholderName("Limited Balance User")
                .initialBalance(new BigDecimal("300.00")).build();
        ResponseEntity<CardResponse> createResponse = restTemplate.postForEntity(
                BASE_URL, createRequest, CardResponse.class);
        UUID cardId = createResponse.getBody().getId();

        // Execute 3 concurrent spends of $200 each (only 1 should succeed)
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<CompletableFuture<ResponseEntity<?>>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < 3; i++) {
            final int index = i;
            CompletableFuture<ResponseEntity<?>> future = CompletableFuture.supplyAsync(() -> {
                SpendRequest spendRequest = SpendRequest.builder().amount(new BigDecimal("200.00")).build();
                HttpHeaders headers = new HttpHeaders();
                headers.set("Idempotency-Key", "concurrent-spend-" + index);
                HttpEntity<SpendRequest> entity = new HttpEntity<>(spendRequest, headers);

                return restTemplate.exchange(
                        BASE_URL + "/" + cardId + "/spends", HttpMethod.POST, entity, Object.class);
            }, executor);

            futures.add(future);
        }

        // Wait for all operations to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);

        // Count successes and failures
        long successCount = futures.stream()
                .mapToInt(future -> {
                    try {
                        ResponseEntity<?> response = future.get();
                        return response.getStatusCode() == HttpStatus.OK ? 1 : 0;
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .sum();

        long failureCount = futures.stream()
                .mapToInt(future -> {
                    try {
                        ResponseEntity<?> response = future.get();
                        return response.getStatusCode() == HttpStatus.CONFLICT ? 1 : 0;
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .sum();

        // Exactly 1 should succeed, 2 should fail due to insufficient funds
        assertThat(successCount).isEqualTo(1);
        assertThat(failureCount).isEqualTo(2);

        // Final balance should be either 100 (if spend succeeded) or 300 (if all failed)
        ResponseEntity<CardResponse> finalCardResponse = restTemplate.getForEntity(
                BASE_URL + "/" + cardId, CardResponse.class);
        BigDecimal finalBalance = finalCardResponse.getBody().getBalance();
        assertThat(finalBalance).satisfiesAnyOf(
                balance -> assertThat(balance).isEqualTo(new BigDecimal("100.00")),
                balance -> assertThat(balance).isEqualTo(new BigDecimal("300.00"))
        );

        executor.shutdown();
    }
}
