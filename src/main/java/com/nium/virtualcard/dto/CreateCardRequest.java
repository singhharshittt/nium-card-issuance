package com.nium.virtualcard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request DTO for creating a new virtual card.
 */
@Data
@Builder
public class CreateCardRequest {
    /**
     * Name of the cardholder.
     */
    @NotBlank(message = "cardholderName is required")
    private String cardholderName;

    /**
     * Initial balance for the newly created card.
     * Must be a non-negative decimal value.
     */
    @DecimalMin(value = "0", message = "initialBalance must be non-negative")
    private BigDecimal initialBalance;
}
