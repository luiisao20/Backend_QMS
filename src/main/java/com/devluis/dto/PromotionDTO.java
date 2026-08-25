package com.devluis.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.devluis.types.DiscountType;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PromotionDTO {
  private Long id;

  @NotNull(message = "El servicio es obligatorio")
  private ServicioDTO servicio;

  @NotBlank(message = "El nombre de la promoción es requerido")
  private String name;

  @NotNull(message = "El tipo de descuento (porcentaje o monto fijo) es obligatorio")
  private DiscountType discountType;

  @NotNull(message = "El valor del descuento es obligatorio")
  @DecimalMin(value = "0.0", inclusive = false, message = "El valor del descuento debe ser mayor a cero")
  private BigDecimal discountValue;

  @NotNull(message = "La fecha de inicio es obligatoria")
  private LocalDate startDate;

  @NotNull(message = "La fecha de fin es obligatoria")
  private LocalDate endDate;

  // Read-only, computed by PromotionService: true when today falls within
  // [startDate, endDate]. Lets any consumer (admin table, public catalogue)
  // render "vigente ahora" without re-deriving date-window logic itself —
  // the exact re-derivation risk this apply's own task description warned
  // about for Servicio.discount.
  private Boolean currentlyActive;

  private OffsetDateTime createdAt;
}
