package com.devluis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.InvoiceLineItem;
import com.devluis.types.InvoiceLineSourceType;
import com.devluis.types.InvoiceStatus;

public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItem, Long> {

  // Duplicate-billing guard: is this Turn/Package/SessionPlan already a line
  // on some OTHER, non-void invoice? Same nested-property derived-query
  // idiom as TurnRepository.existsByScheduleDoctorUuid (traverses
  // `invoice.status` without an explicit join). Used by
  // InvoiceService#buildLineItem before accepting a TURN/PACKAGE/SESSION_PLAN
  // line — see Invoice's docblock for why an invoice's lines are fixed at
  // creation (so "already invoiced" only ever needs to be checked once, at
  // create time). UNVERIFIED AGAINST A REAL DATABASE — see apply report.
  boolean existsBySourceTypeAndSourceIdAndInvoiceStatusNot(
      InvoiceLineSourceType sourceType, Long sourceId, InvoiceStatus status);
}
