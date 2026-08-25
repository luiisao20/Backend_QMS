package com.devluis.dto;

import java.math.BigDecimal;

import com.devluis.types.InvoiceLineSourceType;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Request AND response shape for one Invoice line.
//
// On a CREATE request the client supplies `sourceType` always, plus:
// - TURN / PACKAGE / SESSION_PLAN: `sourceId` (the id to resolve).
// - FREE_LINE: `description` + `amount` (no catalog to resolve).
// This split is NOT expressed with annotations here — same idiom
// ServiceDiscountDTO documents for its own business rule (Jakarta Bean
// Validation cannot express "required only when a sibling field has value
// X"), so InvoiceService validates it explicitly with a clear Spanish
// message per missing field.
//
// On the RESPONSE every field is server-computed: `description`/`amount`
// are the snapshot taken at invoice creation (see InvoiceLineItem),
// `insurerCoveredAmount`/`patientResponsibleAmount` split that amount, and
// `insurerNameSnapshot`/`planNameSnapshot` stay null for any line that was
// never insurer-covered.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InvoiceLineItemDTO {
  private Long id;

  @NotNull(message = "El tipo de origen de la línea es obligatorio")
  private InvoiceLineSourceType sourceType;

  private Long sourceId;

  private String description;

  private BigDecimal amount;

  private BigDecimal insurerCoveredAmount;

  private BigDecimal patientResponsibleAmount;

  private String insurerNameSnapshot;

  private String planNameSnapshot;
}
