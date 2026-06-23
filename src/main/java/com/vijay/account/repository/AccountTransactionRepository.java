package com.vijay.account.repository;

import com.vijay.account.entity.AccountTransaction;
import com.vijay.account.entity.TransactionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {

    Optional<AccountTransaction> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<AccountTransaction> findByAccountIdOrderByEventTimestampAsc(String accountId);

    List<AccountTransaction> findTop10ByAccountIdOrderByEventTimestampDesc(String accountId);

    @Query("""
            select coalesce(sum(transaction.amount), 0)
            from AccountTransaction transaction
            where transaction.accountId = :accountId
              and transaction.type = :type
            """)
    BigDecimal sumAmountByAccountIdAndType(
            @Param("accountId") String accountId,
            @Param("type") TransactionType type
    );

    default BigDecimal getTotalCreditAmountForAccount(String accountId) {
        return sumAmountByAccountIdAndType(accountId, TransactionType.CREDIT);
    }

    default BigDecimal getTotalDebitAmountForAccount(String accountId) {
        return sumAmountByAccountIdAndType(accountId, TransactionType.DEBIT);
    }
}
