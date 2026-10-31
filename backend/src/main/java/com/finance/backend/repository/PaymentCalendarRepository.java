package com.finance.backend.repository;

import com.finance.backend.entity.PaymentCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PaymentCalendarRepository extends JpaRepository<PaymentCalendar, UUID> {
    List<PaymentCalendar> findByCompanyId(UUID companyId);
    List<PaymentCalendar> findByCompanyIdAndDueDateBetween(UUID companyId, LocalDate start, LocalDate end);
    List<PaymentCalendar> findByCompanyIdAndStatus(UUID companyId, String status);
    
    @Query("SELECT SUM(p.amount) FROM PaymentCalendar p WHERE p.company.id = :companyId AND p.status = 'PENDING' AND p.dueDate < :today")
    BigDecimal sumOverduePayments(@Param("companyId") UUID companyId, @Param("today") LocalDate today);
}