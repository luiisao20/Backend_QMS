package com.devluis.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Request body for PUT /api/invoices/{id}/void — a reason is mandatory so
// the audit trail always explains why a financial record was voided.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VoidInvoiceBody {
  @NotBlank(message = "El motivo de anulación es obligatorio")
  private String reason;
}
