package com.devluis.types;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateSchedulesBody {
  @NotNull(message = "El servicio es obligatorio")
  private Long serviceId;
  
  @NotNull(message = "El establecimiento es obligatorio")
  private Long stablishmentId;
  
  private UUID doctorId;
  
  @NotNull(message = "La fecha es obligatoria")
  private LocalDate date;
  
  @NotNull(message = "El intervalo en minutos es obligatorio")
  private Integer intervalMinutes;
}
