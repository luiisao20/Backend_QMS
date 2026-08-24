package com.devluis.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SpecialityDTO {
  private Long id;

  @NotBlank(message = "El campo del nombre es requerido")
  private String name;

  private String description;

  private Boolean active;

  /** Cuántos doctores la tienen asignada. Solo lectura. */
  private Long doctorCount;

  private OffsetDateTime createdAt;
}
