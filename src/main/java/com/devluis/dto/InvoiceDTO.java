package com.devluis.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.devluis.types.InvoiceStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// `patient` is deliberately NOT `@Valid` (only `.getUuid()` is ever read) —
// same idiom as PatientCoverageDTO.patient, so a create request only needs
// to echo the patient's uuid, not their whole profile. `items` IS `@Valid`:
// each InvoiceLineItemDTO's own (light) constraint is worth cascading into.
//
// `total`, `balance`, `status`, `issuedAt`, `voidedAt`, `voidReason` and
// `payments` are ALWAYS server-computed/server-set — a client cannot
// influence them by sending values for these fields.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InvoiceDTO {
  private Long id;

  @NotNull(message = "El paciente es obligatorio")
  private PatientDTO patient;

  private DoctorDTO doctor;

  @NotEmpty(message = "La factura debe tener al menos una línea")
  @Valid
  private List<InvoiceLineItemDTO> items;

  private BigDecimal total;

  private BigDecimal balance;

  private InvoiceStatus status;

  private OffsetDateTime issuedAt;

  private OffsetDateTime voidedAt;

  private String voidReason;

  private UUID voidedByUuid;

  private List<PaymentDTO> payments;
}
