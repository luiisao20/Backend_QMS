package com.devluis.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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
public class SessionPlanDTO {
  private Long id;

  @NotNull(message = "El servicio es obligatorio")
  private ServicioDTO servicio;

  @NotBlank(message = "El nombre del plan es requerido")
  private String name;

  @NotNull(message = "El número de sesiones es obligatorio")
  @Min(value = 1, message = "El número de sesiones debe ser al menos 1")
  private Integer sessionCount;

  @NotNull(message = "El precio del plan es obligatorio")
  @DecimalMin(value = "0.0", message = "El precio no puede ser negativo")
  private BigDecimal price;

  // Read-only, computed by SessionPlanService: price / sessionCount.
  private BigDecimal pricePerSession;

  // Read-only, computed: sessionCount * Servicio net price — "what these
  // sessions would cost bought one at a time today".
  private BigDecimal regularTotal;

  // Read-only, computed: regularTotal - price. Can be negative — surfaced
  // as-is, not clamped, same reasoning as ServicePackageDTO.savings.
  private BigDecimal savings;

  private OffsetDateTime createdAt;
}
