package com.vijay.account.dto;

import com.vijay.account.entity.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record TransactionRequest(
        @NotBlank
        String eventId,

        @NotNull
        TransactionType type,

        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal amount,

        @NotBlank
        String currency,

        @NotNull
        Instant eventTimestamp,

        Map<String, Object> metadata
) {
}
