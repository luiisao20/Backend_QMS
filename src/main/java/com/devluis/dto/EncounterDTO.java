package com.devluis.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

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
public class EncounterDTO {
  private Long id;

  // Required only on create(); ignored on update() (the turn a record
  // belongs to never changes after creation, mirroring how HolidayDTO's
  // "reason" id is the only part of a nested ref the service reads).
  @NotNull(message = "El turno es obligatorio")
  private Long turnId;

  @NotBlank(message = "El motivo de la consulta es obligatorio")
  private String reasonForVisit;

  private String clinicalNotes;

  @NotBlank(message = "El diagnóstico es obligatorio")
  private String diagnosis;

  private OffsetDateTime createdAt;

  // Read-only, derived from turn.schedule.doctor — not settable by a client.
  private UUID doctorUuid;
  private String doctorFullName;
  private LocalDate visitDate;
}
