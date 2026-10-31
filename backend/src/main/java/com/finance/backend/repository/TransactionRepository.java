package com.finance.backend.repository;

import com.finance.backend.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByAccountIdOrderByDateDesc(UUID accountId);
    
    List<Transaction> findByAccountCompanyIdAndDateBetween(UUID companyId, LocalDate start, LocalDate end);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.transactionType = 'INCOME' AND t.date BETWEEN :start AND :end")
    BigDecimal sumIncome(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.transactionType = 'EXPENSE' AND t.date BETWEEN :start AND :end")
    BigDecimal sumExpense(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT t.category as name, SUM(ABS(t.amount)) as value " +
           "FROM Transaction t " +
           "WHERE t.category IS NOT NULL AND t.category <> '' " +
           "GROUP BY t.category")
    List<Map<String, Object>> findAllCategoryDistribution();
}