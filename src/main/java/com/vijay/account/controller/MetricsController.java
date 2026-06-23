package com.vijay.account.controller;

import com.vijay.account.entity.TransactionType;
import com.vijay.account.repository.AccountTransactionRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MetricsController {

    private final AccountTransactionRepository accountTransactionRepository;

    @Value("${spring.application.name}")
    private String serviceName;

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
