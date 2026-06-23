package com.vijay.account.dto;

import java.math.BigDecimal;

public record AccountBalanceResponse(
        String accountId,
        BigDecimal balance,
        String currency,
        String traceId
) {
}
