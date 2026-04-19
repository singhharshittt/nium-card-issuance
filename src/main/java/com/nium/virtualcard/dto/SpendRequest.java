package com.nium.virtualcard.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for spending from a card.
 * Requires an Idempotency-Key header for idempotent operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendRequest {
    /**
     * Amount to deduct from the card balance.
     * Must be a positive decimal value.
     */
    @DecimalMin(value = "0", message = "amount must be positive")
    private BigDecimal amount;
}
