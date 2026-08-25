package com.devluis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrescriptionItemDTO {
  private Long id;

  @NotBlank(message = "El medicamento es obligatorio")
  private String medication;

  @NotBlank(message = "La dosis es obligatoria")
  private String dosage;

  @NotBlank(message = "La frecuencia es obligatoria")
  private String frequency;

  @NotBlank(message = "La duración es obligatoria")
  private String duration;

  private String instructions;
}
