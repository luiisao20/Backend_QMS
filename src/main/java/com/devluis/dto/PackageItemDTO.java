package com.devluis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.Min;
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
public class PackageItemDTO {
  private Long id;

  @NotNull(message = "El servicio de la línea es obligatorio")
  private ServicioDTO servicio;

  @NotNull(message = "La cantidad es obligatoria")
  @Min(value = 1, message = "La cantidad debe ser al menos 1")
  private Integer quantity;
}
