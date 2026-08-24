package com.devluis.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

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
public class HolidayDTO {
  private Long id;

  @NotNull(message = "El campo de la fecha es obligatorio")
  private LocalDate date;

  @NotBlank(message = "El campo del nombre es requerido")
  private String name;

  /**
   * Opcional, y ahi esta la utilidad: en null es feriado nacional y aplica a
   * todas las sedes. Se manda anidado, como en `ScheduleDTO`, y el servicio lee
   * el id de adentro.
   */
  private StablishmentDTO stablishment;

  private OffsetDateTime createdAt;
}
