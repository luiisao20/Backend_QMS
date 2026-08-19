package com.devluis.dto;

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
public class ServicioDTO {
  private Long id;

  @NotBlank(message = "El nombre del servicio es requerido")
  private String name;

  @NotNull(message = "El precio es requerido")
  private Float price;

  private Float discount;
}
