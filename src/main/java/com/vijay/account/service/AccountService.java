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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service component that implements account transaction processing and balance
 * calculation for the Account Service.
 *
 * <p>This service owns the core ledger behavior for accounts. It applies credit
 * and debit events, enforces defensive idempotency through unique event
 * identifiers, calculates balances as total credits minus total debits, and
 * retrieves recent account activity for account detail views. The service uses
 * {@link BigDecimal} for all monetary calculations to preserve decimal
 * precision.</p>
 *
 * @see AccountTransactionRepository
 * @see TransactionRequest
 * @see TransactionResponse
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private static final String STATUS_APPLIED = "APPLIED";
    private static final String STATUS_DUPLICATE = "DUPLICATE";

    private final AccountTransactionRepository accountTransactionRepository;

    /**
     * Applies a transaction event to an account.
     *
     * <p>If the request's event identifier already exists, the persisted
     * transaction is returned with duplicate status and no new database row is
     * created. Otherwise, the transaction is saved and returned with applied
     * status. This behavior supports idempotent event ingestion when upstream
     * systems retry delivery.</p>
     *
     * @param accountId the account identifier that receives the transaction
     * @param request the transaction request containing event, type, amount,
     *        currency, and event timestamp
     * @param traceId the trace identifier associated with the request
     * @return the transaction response representing either the newly applied
     *         transaction or the previously persisted duplicate event
     * @throws DataIntegrityViolationException if persistence fails for a
     *         database-integrity reason other than a recoverable duplicate
     *         {@code eventId}
     */
    @Transactional
    public TransactionResponse applyTransaction(String accountId, TransactionRequest request, String traceId) {
        return accountTransactionRepository.findByEventId(request.eventId())
                .map(transaction -> {
                    log.info("Duplicate transaction event received eventId={} accountId={}", request.eventId(), accountId);
                    return toTransactionResponse(transaction, STATUS_DUPLICATE);
                })
                .orElseGet(() -> saveTransaction(accountId, request, traceId));
    }

    /**
     * Returns the current balance for an account.
     *
     * <p>The balance is calculated from persisted transactions using the
     * business rule {@code total CREDIT amount - total DEBIT amount}. The
     * response currency is resolved from the most recent transaction for the
     * account when one exists.</p>
     *
     * @param accountId the account identifier for which the balance is requested
     * @param traceId the trace identifier associated with the request
     * @return the current balance response for the account
     */
    @Transactional(readOnly = true)
    public AccountBalanceResponse getBalance(String accountId, String traceId) {
        log.info("Balance lookup requested accountId={}", accountId);

        return new AccountBalanceResponse(
                accountId,
                calculateBalance(accountId),
                resolveCurrency(accountId),
                traceId
        );
    }

    /**
     * Returns account summary details and recent transaction activity.
     *
     * <p>The response includes the calculated account balance, the resolved
     * currency, and the most recent transactions ordered by event timestamp
     * descending as provided by the repository.</p>
     *
     * @param accountId the account identifier for which details are requested
     * @param traceId the trace identifier associated with the request
     * @return account details including balance, currency, and recent
     *         transactions
     */
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

    /**
     * Persists a new transaction for an account.
     *
     * <p>The method also handles the race condition where another request inserts
     * the same event identifier after the initial duplicate check but before this
     * save completes. In that case, the existing transaction is returned as an
     * idempotent duplicate.</p>
     *
     * @param accountId the account identifier that receives the transaction
     * @param request the transaction request to persist
     * @param traceId the trace identifier associated with the request
     * @return the saved transaction response, or an existing duplicate
     *         transaction response when a concurrent duplicate is detected
     * @throws DataIntegrityViolationException if the save fails and the existing
     *         transaction cannot be found by event identifier
     */
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
            AccountTransaction savedTransaction = accountTransactionRepository.save(transaction);
            log.info(
                    "Transaction applied eventId={} accountId={} type={} amount={} currency={}",
                    savedTransaction.getEventId(),
                    savedTransaction.getAccountId(),
                    savedTransaction.getType(),
                    savedTransaction.getAmount(),
                    savedTransaction.getCurrency()
            );

            return toTransactionResponse(savedTransaction, STATUS_APPLIED);
        } catch (DataIntegrityViolationException ex) {
            return accountTransactionRepository.findByEventId(request.eventId())
                    .map(existingTransaction -> {
                        log.info("Duplicate transaction event received eventId={} accountId={}", request.eventId(), accountId);
                        return toTransactionResponse(existingTransaction, STATUS_DUPLICATE);
                    })
                    .orElseThrow(() -> ex);
        }
    }

    /**
     * Calculates the account balance from persisted transaction totals.
     *
     * @param accountId the account identifier for which totals are calculated
     * @return total credited amount minus total debited amount
     */
    private BigDecimal calculateBalance(String accountId) {
        BigDecimal totalCredits = accountTransactionRepository.getTotalCreditAmountForAccount(accountId);
        BigDecimal totalDebits = accountTransactionRepository.getTotalDebitAmountForAccount(accountId);

        return nullToZero(totalCredits).subtract(nullToZero(totalDebits));
    }

    /**
     * Resolves the display currency for an account from its most recent
     * transaction.
     *
     * @param accountId the account identifier for which currency is resolved
     * @return the currency of the most recent transaction, or {@code null} when
     *         the account has no transactions
     */
    private String resolveCurrency(String accountId) {
        return accountTransactionRepository.findTop10ByAccountIdOrderByEventTimestampDesc(accountId)
                .stream()
                .findFirst()
                .map(AccountTransaction::getCurrency)
                .orElse(null);
    }

    /**
     * Converts a persisted transaction entity to the API response shape.
     *
     * @param transaction the persisted account transaction entity
     * @param status the response status value, such as {@code APPLIED} or
     *        {@code DUPLICATE}
     * @return the transaction response exposed by the service API
     */
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

    /**
     * Converts a nullable monetary value to zero.
     *
     * @param value the monetary value returned by an aggregate query
     * @return {@link BigDecimal#ZERO} when {@code value} is {@code null};
     *         otherwise, the original value
     */
    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
