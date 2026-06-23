package com.vijay.account.service;

import com.vijay.account.dto.AccountBalanceResponse;
import com.vijay.account.dto.AccountDetailsResponse;
import com.vijay.account.dto.TransactionRequest;
import com.vijay.account.dto.TransactionResponse;
import com.vijay.account.entity.AccountTransaction;
import com.vijay.account.repository.AccountTransactionRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final String STATUS_APPLIED = "APPLIED";
    private static final String STATUS_DUPLICATE = "DUPLICATE";

    private final AccountTransactionRepository accountTransactionRepository;

    @Transactional
    public TransactionResponse applyTransaction(String accountId, TransactionRequest request, String traceId) {
        return accountTransactionRepository.findByEventId(request.eventId())
                .map(transaction -> toTransactionResponse(transaction, STATUS_DUPLICATE))
                .orElseGet(() -> saveTransaction(accountId, request, traceId));
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse getBalance(String accountId, String traceId) {
        return new AccountBalanceResponse(
                accountId,
                calculateBalance(accountId),
                resolveCurrency(accountId),
                traceId
        );
    }

    @Transactional(readOnly = true)
    public AccountDetailsResponse getAccountDetails(String accountId, String traceId) {
        List<TransactionResponse> recentTransactions = accountTransactionRepository
                .findTop10ByAccountIdOrderByEventTimestampDesc(accountId)
                .stream()
                .map(transaction -> toTransactionResponse(transaction, STATUS_APPLIED))
                .toList();

        return new AccountDetailsResponse(
                accountId,
                calculateBalance(accountId),
                recentTransactions.stream()
                        .findFirst()
                        .map(TransactionResponse::currency)
                        .orElse(null),
                recentTransactions
        );
    }

    private TransactionResponse saveTransaction(String accountId, TransactionRequest request, String traceId) {
        AccountTransaction transaction = AccountTransaction.builder()
                .eventId(request.eventId())
                .accountId(accountId)
                .type(request.type())
                .amount(request.amount())
                .currency(request.currency())
                .eventTimestamp(request.eventTimestamp())
                .traceId(traceId)
                .build();

        try {
            return toTransactionResponse(
                    accountTransactionRepository.save(transaction),
                    STATUS_APPLIED
            );
        } catch (DataIntegrityViolationException ex) {
            return accountTransactionRepository.findByEventId(request.eventId())
                    .map(existingTransaction -> toTransactionResponse(existingTransaction, STATUS_DUPLICATE))
                    .orElseThrow(() -> ex);
        }
    }

    private BigDecimal calculateBalance(String accountId) {
        BigDecimal totalCredits = accountTransactionRepository.getTotalCreditAmountForAccount(accountId);
        BigDecimal totalDebits = accountTransactionRepository.getTotalDebitAmountForAccount(accountId);

        return nullToZero(totalCredits).subtract(nullToZero(totalDebits));
    }

    private String resolveCurrency(String accountId) {
        return accountTransactionRepository.findTop10ByAccountIdOrderByEventTimestampDesc(accountId)
                .stream()
                .findFirst()
                .map(AccountTransaction::getCurrency)
                .orElse(null);
    }

    private TransactionResponse toTransactionResponse(AccountTransaction transaction, String status) {
        return new TransactionResponse(
                transaction.getEventId(),
                transaction.getAccountId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getEventTimestamp(),
                transaction.getTraceId(),
                status
        );
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
