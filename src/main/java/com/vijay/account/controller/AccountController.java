package com.vijay.account.controller;

import com.vijay.account.dto.AccountBalanceResponse;
import com.vijay.account.dto.AccountDetailsResponse;
import com.vijay.account.dto.TransactionRequest;
import com.vijay.account.dto.TransactionResponse;
import com.vijay.account.service.AccountService;
import com.vijay.account.tracing.TraceIdFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes account transaction and balance operations for
 * the Account Service.
 *
 * <p>The controller is the HTTP boundary for the ledger-facing account API. It
 * validates inbound transaction requests, propagates trace identifiers, delegates
 * business processing to {@link AccountService}, and maps idempotent transaction
 * outcomes to meaningful HTTP status codes.</p>
 *
 * @see AccountService
 * @see TraceIdFilter
 */
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private static final String STATUS_DUPLICATE = "DUPLICATE";

    private final AccountService accountService;

    /**
     * Applies a credit or debit transaction to the specified account.
     *
     * <p>New transactions return {@link HttpStatus#CREATED}. Replayed requests
     * with an existing event identifier are treated as idempotent duplicates and
     * return {@link HttpStatus#OK} without creating another transaction row.</p>
     *
     * @param accountId the account identifier from the request path
     * @param request the validated transaction request body
     * @param traceId the optional trace identifier from the {@code X-Trace-Id}
     *                request header
     * @return a response entity containing the applied or duplicate transaction
     *         response and the appropriate HTTP status code
     * @throws org.springframework.web.bind.MethodArgumentNotValidException if the
     *         request body fails validation
     * @throws org.springframework.http.converter.HttpMessageNotReadableException
     *         if the request body is malformed or contains an invalid enum value
     */
    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> applyTransaction(
            @PathVariable String accountId,
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader(value = TraceIdFilter.TRACE_ID_HEADER, required = false) String traceId
    ) {
        TransactionResponse response = accountService.applyTransaction(accountId, request, resolveTraceId(traceId));
        HttpStatus status = STATUS_DUPLICATE.equals(response.status()) ? HttpStatus.OK : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Returns the current balance for an account.
     *
     * <p>The balance is calculated by the service layer as total credits minus
     * total debits. The trace identifier is forwarded so the response can be
     * correlated with request logs.</p>
     *
     * @param accountId the account identifier from the request path
     * @param traceId the optional trace identifier from the {@code X-Trace-Id}
     *                request header
     * @return a response entity containing the account balance
     */
    @GetMapping("/{accountId}/balance")
    public ResponseEntity<AccountBalanceResponse> getBalance(
            @PathVariable String accountId,
            @RequestHeader(value = TraceIdFilter.TRACE_ID_HEADER, required = false) String traceId
    ) {
        return ResponseEntity.ok(accountService.getBalance(accountId, resolveTraceId(traceId)));
    }

    /**
     * Returns account summary information and recent transactions.
     *
     * <p>The response includes the calculated balance and the most recent
     * transactions for the account, allowing clients to render an account detail
     * view without making separate transaction-history calls.</p>
     *
     * @param accountId the account identifier from the request path
     * @param traceId the optional trace identifier from the {@code X-Trace-Id}
     *                request header
     * @return a response entity containing account details and recent
     *         transactions
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDetailsResponse> getAccountDetails(
            @PathVariable String accountId,
            @RequestHeader(value = TraceIdFilter.TRACE_ID_HEADER, required = false) String traceId
    ) {
        return ResponseEntity.ok(accountService.getAccountDetails(accountId, resolveTraceId(traceId)));
    }

    /**
     * Resolves the trace identifier for downstream service calls.
     *
     * <p>The inbound header value is preferred. When the header is absent, the
     * value generated by {@link TraceIdFilter} and stored in MDC is used.</p>
     *
     * @param traceId the optional trace identifier supplied by the request
     *        header
     * @return the supplied trace identifier, or the trace identifier currently
     *         stored in MDC
     */
    private String resolveTraceId(String traceId) {
        return traceId != null ? traceId : MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
    }
}
