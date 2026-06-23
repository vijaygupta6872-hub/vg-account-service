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

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private static final String STATUS_DUPLICATE = "DUPLICATE";

    private final AccountService accountService;

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

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<AccountBalanceResponse> getBalance(
            @PathVariable String accountId,
            @RequestHeader(value = TraceIdFilter.TRACE_ID_HEADER, required = false) String traceId
    ) {
        return ResponseEntity.ok(accountService.getBalance(accountId, resolveTraceId(traceId)));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDetailsResponse> getAccountDetails(
            @PathVariable String accountId,
            @RequestHeader(value = TraceIdFilter.TRACE_ID_HEADER, required = false) String traceId
    ) {
        return ResponseEntity.ok(accountService.getAccountDetails(accountId, resolveTraceId(traceId)));
    }

    private String resolveTraceId(String traceId) {
        return traceId != null ? traceId : MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
    }
}
