package com.devluis.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Request body for PUT /api/services/{id}/discount — deliberately just the
// one field. Not @DecimalMin-validated here: Jakarta Bean Validation's
// @DecimalMin does not support Float/Double (only BigDecimal/BigInteger/
// integral types and CharSequence), so the ">= 0" business rule lives in
// ServicioService#updateDiscount instead, same place every other manual
// business rule in this codebase lives.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceDiscountDTO {
  @NotNull(message = "El descuento es requerido")
  private Float discount;
}
