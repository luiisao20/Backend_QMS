package com.devluis.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Request body for PUT /api/admin-modules/{moduleKey} — same "small
// purpose-built body" idiom as VoidInvoiceBody/RejectClaimBody. `enabled` is
// a Boolean wrapper (not a primitive boolean) on purpose: a primitive would
// silently deserialize a missing field to false instead of letting
// @NotNull reject the request.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ModuleToggleBody {
  @NotNull(message = "El estado habilitado/deshabilitado es requerido")
  private Boolean enabled;
}
