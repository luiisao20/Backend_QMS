package com.devluis.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.dto.InvoiceStatusSummaryRow;
import com.devluis.entity.Invoice;
import com.devluis.types.InvoiceStatus;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

  // GET /api/invoices/me and the staff GET /api/patients/{patientId}/invoices
  // screen — same "List/Page, patientUuid-scoped" precedent as
  // EncounterRepository.findByPatientUuid. UNVERIFIED AGAINST A REAL
  // DATABASE — see apply report.
  @Query("SELECT i FROM Invoice i WHERE i.patient.uuid = :patientUuid ORDER BY i.issuedAt DESC")
  Page<Invoice> findByPatientUuid(@Param("patientUuid") UUID patientUuid, Pageable pageable);

  // Staff bare-collection browse (GET /api/invoices), same
  // "(:param IS NULL OR ...)" optional-filter idiom as
  // TurnRepository.countByDayAndStatus. UNVERIFIED AGAINST A REAL DATABASE.
  @Query("SELECT i FROM Invoice i WHERE (:patientUuid IS NULL OR i.patient.uuid = :patientUuid) "
      + "AND (:status IS NULL OR i.status = :status) ORDER BY i.issuedAt DESC")
  Page<Invoice> search(
      @Param("patientUuid") UUID patientUuid, @Param("status") InvoiceStatus status, Pageable pageable);

  // Duplicate-billing guard support: has this Turn/Package/SessionPlan
  // already been invoiced under a non-void invoice? See
  // InvoiceLineItemRepository for the actual check — kept here only as a
  // note that Invoice.status is what "non-void" filters against.

  // finanzas/contabilidad aggregate: invoices ISSUED within [from, to],
  // grouped by their CURRENT status. Same GROUP BY + constructor-expression
  // shape as TurnRepository's metrics queries — aggregated in the database,
  // never by looping over Invoice rows in Java. UNVERIFIED AGAINST A REAL
  // DATABASE.
  @Query("SELECT new com.devluis.dto.InvoiceStatusSummaryRow(i.status, COUNT(i), COALESCE(SUM(i.total),0)) "
      + "FROM Invoice i WHERE i.issuedAt BETWEEN :from AND :to GROUP BY i.status")
  List<InvoiceStatusSummaryRow> countAndSumByStatusInRange(
      @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

  // finanzas/contabilidad "outstandingNow" half — see AccountingService for
  // why this is NOT period-bounded. Paired with
  // PaymentRepository#sumAmountForNonVoidInvoices; the subtraction happens
  // in AccountingService, both halves are DB-side SUMs. UNVERIFIED AGAINST A
  // REAL DATABASE.
  @Query("SELECT COALESCE(SUM(i.total),0) FROM Invoice i WHERE i.status <> com.devluis.types.InvoiceStatus.VOID")
  BigDecimal sumTotalForNonVoidInvoices();
}
