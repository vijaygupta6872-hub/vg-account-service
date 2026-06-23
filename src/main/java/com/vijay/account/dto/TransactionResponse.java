package com.vijay.account.dto;

import com.vijay.account.entity.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        String eventId,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        String traceId,
        String status
) {
}
