package com.devluis.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.dto.PaymentMethodSummaryRow;
import com.devluis.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

  // Embeds into InvoiceDTO.payments and backs the overpayment/balance check
  // in PaymentService — a bounded, small per-invoice list, same "sum a small
  // bounded collection in Java" precedent as ServicePackage's own
  // itemsTotal (see ServicePackage's docblock), NOT a case that needs a
  // database-side SUM. UNVERIFIED AGAINST A REAL DATABASE — see apply
  // report.
  List<Payment> findByInvoiceIdOrderByPaidAtAsc(Long invoiceId);

  // finanzas/contabilidad aggregate: money actually RECEIVED within
  // [from, to] (bounded by paidAt), grouped by method. Aggregated in the
  // database via GROUP BY + SUM, never by looping over Payment rows in
  // Java. UNVERIFIED AGAINST A REAL DATABASE.
  @Query("SELECT new com.devluis.dto.PaymentMethodSummaryRow(p.method, COUNT(p), COALESCE(SUM(p.amount),0)) "
      + "FROM Payment p WHERE p.paidAt BETWEEN :from AND :to GROUP BY p.method")
  List<PaymentMethodSummaryRow> countAndSumByMethodInRange(
      @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

  // Paired with InvoiceRepository#sumTotalForNonVoidInvoices for
  // AccountingService's "outstandingNow" figure. UNVERIFIED AGAINST A REAL
  // DATABASE.
  @Query("SELECT COALESCE(SUM(p.amount),0) FROM Payment p "
      + "WHERE p.invoice.status <> com.devluis.types.InvoiceStatus.VOID")
  BigDecimal sumAmountForNonVoidInvoices();
}
