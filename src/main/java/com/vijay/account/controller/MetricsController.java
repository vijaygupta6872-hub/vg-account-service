package com.vijay.account.controller;

import com.vijay.account.entity.TransactionType;
import com.vijay.account.repository.AccountTransactionRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes custom Account Service metrics.
 *
 * <p>The metrics endpoint provides lightweight business and operational counters
 * for transaction volume and account usage. These metrics are intended for quick
 * service inspection and local diagnostics, complementing the standard Spring
 * Boot actuator endpoints.</p>
 */
@RestController
@RequiredArgsConstructor
public class MetricsController {

    private final AccountTransactionRepository accountTransactionRepository;

    @Value("${spring.application.name}")
    private String serviceName;

    /**
     * Returns transaction and account metrics for the service.
     *
     * <p>The response includes total transaction volume, transaction counts by
     * type, and the number of distinct accounts that have recorded transactions.
     * Values are calculated from the persisted account transaction table.</p>
     *
     * @return a map containing the service name, total transaction count, credit
     *         transaction count, debit transaction count, and unique account
     *         count
     * @throws org.springframework.dao.DataAccessException if the repository
     *         cannot query the backing database
     */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return Map.of(
                "serviceName", serviceName,
                "totalTransactionCount", accountTransactionRepository.count(),
                "creditTransactionCount", accountTransactionRepository.countByType(TransactionType.CREDIT),
                "debitTransactionCount", accountTransactionRepository.countByType(TransactionType.DEBIT),
                "uniqueAccountCount", accountTransactionRepository.countDistinctAccountIds()
        );
    }
}
