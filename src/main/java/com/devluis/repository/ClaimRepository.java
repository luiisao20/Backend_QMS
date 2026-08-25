package com.devluis.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.dto.ClaimStatusSummaryRow;
import com.devluis.entity.Claim;
import com.devluis.types.ClaimStatus;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

  // Guard against a second in-flight claim for the same invoice — see
  // ClaimService#create. UNVERIFIED AGAINST A REAL DATABASE — see apply
  // report.
  boolean existsByInvoiceIdAndStatusIn(Long invoiceId, List<ClaimStatus> statuses);

  List<Claim> findByInvoiceId(Long invoiceId);

  // Staff bare-collection browse (GET /api/claims), same optional-filter
  // idiom as InvoiceRepository#search. UNVERIFIED AGAINST A REAL DATABASE.
  @Query("SELECT c FROM Claim c WHERE (:invoiceId IS NULL OR c.invoice.id = :invoiceId) "
      + "AND (:status IS NULL OR c.status = :status) ORDER BY c.submittedAt DESC")
  Page<Claim> search(
      @Param("invoiceId") Long invoiceId, @Param("status") ClaimStatus status, Pageable pageable);

  // finanzas/reclamos + finanzas/contabilidad aggregate: claims SUBMITTED
  // within [from, to], grouped by CURRENT status. Aggregated in the
  // database. UNVERIFIED AGAINST A REAL DATABASE.
  @Query("SELECT new com.devluis.dto.ClaimStatusSummaryRow(c.status, COUNT(c), COALESCE(SUM(c.amountClaimed),0)) "
      + "FROM Claim c WHERE c.submittedAt BETWEEN :from AND :to GROUP BY c.status")
  List<ClaimStatusSummaryRow> countAndSumByStatusInRange(
      @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
