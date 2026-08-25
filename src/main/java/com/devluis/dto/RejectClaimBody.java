package com.devluis.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Request body for PUT /api/claims/{id}/reject — same "reason mandatory"
// discipline as VoidInvoiceBody.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RejectClaimBody {
  @NotBlank(message = "El motivo de rechazo es obligatorio")
  private String reason;
}
