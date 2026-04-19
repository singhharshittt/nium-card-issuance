package com.nium.virtualcard.controller;

import com.nium.virtualcard.dto.CardResponse;
import com.nium.virtualcard.dto.CreateCardRequest;
import com.nium.virtualcard.dto.SpendRequest;
import com.nium.virtualcard.dto.TopUpRequest;
import com.nium.virtualcard.dto.TransactionResponse;
import com.nium.virtualcard.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST API Controller for virtual card operations.
 *
 * Provides endpoints for:
 * - Card creation and retrieval
 * - Balance top-ups with idempotency
 * - Balance spends with idempotency
 * - Transaction history retrieval
 *
 * All financial operations (top-up, spend) require an Idempotency-Key header
 * to ensure at-most-once semantics across retries.
 */
@RestController
@RequestMapping("/cards")
@Tag(name = "Virtual Card API", description = "Manage virtual cards, top-ups, spends, and transaction history")
@RequiredArgsConstructor
@Slf4j
public class CardController {

    private final CardService cardService;

    /**
     * Create a new virtual card with an initial balance.
     *
     * @param request the card creation request containing cardholder name and initial balance
     * @return 201 Created with the created card details
     * @throws IllegalArgumentException if the initial balance is negative
     */
    @PostMapping
    @Operation(summary = "Create a new virtual card",
            description = "Creates a new virtual card with the specified cardholder name and initial balance. " +
                    "The card is created in ACTIVE status and a CARD_ISSUANCE transaction is recorded.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Card successfully created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CardResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g., negative initial balance or missing cardholder name)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CardResponse> createCard(
            @Valid @RequestBody CreateCardRequest request) {
        log.info("POST /cards - Creating card for: {}", request.getCardholderName());
        CardResponse response = cardService.createCard(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieve details for a specific card.
     *
     * @param cardId the UUID of the card to retrieve
     * @return 200 OK with the card details, or 404 if not found
     */
    @GetMapping("/{cardId}")
    @Operation(summary = "Get card details",
            description = "Retrieves the current details of a card including its balance, status, and creation date.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Card found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CardResponse.class))),
            @ApiResponse(responseCode = "404", description = "Card not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CardResponse> getCard(
            @Parameter(description = "The UUID of the card", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID cardId) {
        log.info("GET /cards/{} - Retrieving card details", cardId);
        CardResponse response = cardService.getCard(cardId);
        return ResponseEntity.ok(response);
    }

    /**
     * Add funds to a card (top-up).
     *
     * This operation is idempotent: sending the same request with the same Idempotency-Key
     * multiple times will only result in one top-up being applied.
     *
     * @param cardId the UUID of the card
     * @param request the top-up request containing the amount
     * @param idempotencyKey the idempotency key (required for idempotent semantics)
     * @return 200 OK with updated card details, or 409 if card is not active or idempotency conflict
     */
    @PostMapping("/{cardId}/top-ups")
    @Operation(summary = "Top-up a card",
            description = "Adds funds to a card. This operation is idempotent: the same Idempotency-Key " +
                    "will always produce the same result. Requires the card to be in ACTIVE status.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Top-up successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CardResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g., non-positive amount or missing Idempotency-Key header)"),
            @ApiResponse(responseCode = "404", description = "Card not found"),
            @ApiResponse(responseCode = "409", description = "Conflict: card is not ACTIVE or concurrent update failed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CardResponse> topUp(
            @Parameter(description = "The UUID of the card", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID cardId,
            @Valid @RequestBody TopUpRequest request,
            @Parameter(description = "Unique idempotency key for this operation (required)", required = true, example = "top-up-550e8400-e29b-41d4-a716-446655440000-123")
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        log.info("POST /cards/{}/top-ups - Top-up request for amount: {}, idempotencyKey: {}",
                cardId, request.getAmount(), idempotencyKey);
        CardResponse response = cardService.topUp(cardId, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    /**
     * Deduct funds from a card (spend).
     *
     * This operation is idempotent: sending the same request with the same Idempotency-Key
     * multiple times will only result in one spend being applied.
     *
     * @param cardId the UUID of the card
     * @param request the spend request containing the amount
     * @param idempotencyKey the idempotency key (required for idempotent semantics)
     * @return 200 OK with updated card details, or 409 if insufficient funds, card not active, or idempotency conflict
     */
    @PostMapping("/{cardId}/spends")
    @Operation(summary = "Spend from a card",
            description = "Deducts funds from a card. This operation is idempotent: the same Idempotency-Key " +
                    "will always produce the same result. Requires the card to be in ACTIVE status and to have " +
                    "sufficient funds. Returns 409 Conflict if insufficient funds or concurrent updates exhaust retries.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Spend successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CardResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g., non-positive amount or missing Idempotency-Key header)"),
            @ApiResponse(responseCode = "404", description = "Card not found"),
            @ApiResponse(responseCode = "409", description = "Conflict: insufficient funds, card not ACTIVE, or concurrent update failed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CardResponse> spend(
            @Parameter(description = "The UUID of the card", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID cardId,
            @Valid @RequestBody SpendRequest request,
            @Parameter(description = "Unique idempotency key for this operation (required)", required = true, example = "spend-550e8400-e29b-41d4-a716-446655440000-456")
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        log.info("POST /cards/{}/spends - Spend request for amount: {}, idempotencyKey: {}",
                cardId, request.getAmount(), idempotencyKey);
        CardResponse response = cardService.spend(cardId, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve the complete transaction history for a card.
     *
     * @param cardId the UUID of the card
     * @return 200 OK with a list of transactions ordered by creation time (newest first), or 404 if card not found
     */
    @GetMapping("/{cardId}/transactions")
    @Operation(summary = "Get transaction history",
            description = "Retrieves all transactions for a card, ordered by creation time (newest first). " +
                    "Includes card issuance, top-ups, spends, and their statuses (successful/declined).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transactions retrieved",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "404", description = "Card not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<TransactionResponse>> getTransactions(
            @Parameter(description = "The UUID of the card", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID cardId) {
        log.info("GET /cards/{}/transactions - Retrieving transaction history", cardId);
        List<TransactionResponse> response = cardService.getTransactions(cardId);
        return ResponseEntity.ok(response);
    }
}
