package com.vijay.account.dto;

import java.math.BigDecimal;
import java.util.List;

public record AccountDetailsResponse(
        String accountId,
        BigDecimal balance,
        String currency,
        List<TransactionResponse> recentTransactions
) {
}
