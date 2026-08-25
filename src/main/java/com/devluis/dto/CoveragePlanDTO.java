package com.devluis.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
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
public class CoveragePlanDTO {
  private Long id;

  @NotNull(message = "La aseguradora es obligatoria")
  private InsurerDTO insurer;

  @NotBlank(message = "El nombre del plan es requerido")
  private String name;

  @NotNull(message = "El porcentaje de cobertura es obligatorio")
  @Min(value = 0, message = "El porcentaje de cobertura no puede ser negativo")
  @Max(value = 100, message = "El porcentaje de cobertura no puede superar 100")
  private Integer coveragePercentage;

  @DecimalMin(value = "0.0", message = "El copago no puede ser negativo")
  private BigDecimal copayAmount;

  private OffsetDateTime createdAt;
}
