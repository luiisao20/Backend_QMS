package com.devluis.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.devluis.types.ClaimStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Request (create): only `invoiceId` is client-supplied — everything else
// (insurerName/planName/amountClaimed) is derived by ClaimService from that
// invoice's own line-item snapshots, never entered by hand. Every other
// field is response-only / server-set.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaimDTO {
  private Long id;

  @NotNull(message = "La factura es obligatoria")
  private Long invoiceId;

  private String insurerName;

  private String planName;

  private BigDecimal amountClaimed;

  private ClaimStatus status;

  private OffsetDateTime submittedAt;

  private OffsetDateTime decidedAt;

  private OffsetDateTime paidAt;

  private String rejectionReason;
}
