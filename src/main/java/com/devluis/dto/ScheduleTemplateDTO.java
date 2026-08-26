package com.devluis.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduleTemplateDTO {
  private Long id;

  @NotNull(message = "El establecimiento es obligatorio")
  private StablishmentDTO stablishment;

  @NotNull(message = "El servicio es obligatorio")
  private ServicioDTO servicio;

  // Absent/null = pool slot, not tied to one specific doctor. Only `uuid` is
  // read by the service.
  private DoctorDTO doctor;

  /**
   * Consultorio por defecto de esta jornada. Opcional: las plantillas que ya
   * existen no tienen uno, y al llamar el turno el operador puede elegirlo.
   * Debe pertenecer a la MISMA sede de la plantilla; el service lo valida.
   */
  private ConsultorioDTO consultorio;

  @NotNull(message = "El día de la semana es obligatorio")
  private DayOfWeek dayOfWeek;

  @NotNull(message = "La hora de inicio es obligatoria")
  private LocalTime startTime;

  @NotNull(message = "La hora de fin es obligatoria")
  private LocalTime endTime;

  @NotNull(message = "El intervalo de los turnos en minutos es obligatorio")
  @Positive(message = "El intervalo de los turnos debe ser mayor a cero")
  private Integer slotIntervalMinutes;

  @NotNull(message = "La fecha de inicio de vigencia es obligatoria")
  private LocalDate validFrom;

  // Null = vigencia abierta (sin fecha de fin).
  private LocalDate validUntil;

  private OffsetDateTime createdAt;
}
