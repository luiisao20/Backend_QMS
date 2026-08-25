package com.devluis.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.devluis.types.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Request: amount + method (+ optional free-text reference). `amount` is
// BigDecimal (unlike Servicio.price/discount) so @DecimalMin applies
// directly here, same reasoning PromotionDTO.discountValue already
// documents. `receivedByUuid`/`paidAt`/`claimId` are server-set,
// response-only fields — never trusted from the request body.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentDTO {
  private Long id;

  @NotNull(message = "El monto del pago es obligatorio")
  @DecimalMin(value = "0.0", inclusive = false, message = "El monto del pago debe ser mayor a cero")
  private BigDecimal amount;

  @NotNull(message = "El método de pago es obligatorio")
  private PaymentMethod method;

  private String reference;

  private UUID receivedByUuid;

  private OffsetDateTime paidAt;

  private Long claimId;
}
