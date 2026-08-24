package com.devluis.types;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReassignTurnBody {
  @NotNull(message = "El ID del nuevo horario (scheduleId) es requerido")
  private Long scheduleId;
}
