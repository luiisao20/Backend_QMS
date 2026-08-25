package com.devluis.types;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// Companion to GenerateSchedulesBody for the template-driven generation path
// (ScheduleService#generateSchedulesFromTemplates). Deliberately does NOT
// carry intervalMinutes/date the way GenerateSchedulesBody does: the interval
// (and start/end hour) now comes from the applicable ScheduleTemplate for
// each date's weekday, and `date` becomes a [from, to] PERIOD since the whole
// point of a template is generating more than one day per call.
@Data
public class GenerateSchedulesFromTemplateBody {
  @NotNull(message = "El establecimiento es obligatorio")
  private Long stablishmentId;

  @NotNull(message = "El servicio es obligatorio")
  private Long serviceId;

  private UUID doctorId;

  @NotNull(message = "La fecha de inicio del período es obligatoria")
  private LocalDate from;

  @NotNull(message = "La fecha de fin del período es obligatoria")
  private LocalDate to;
}
