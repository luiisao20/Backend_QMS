package com.devluis.dto;

import java.math.BigDecimal;

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
public class ServicioDTO {
  private Long id;

  @NotBlank(message = "El nombre del servicio es requerido")
  private String name;

  @NotNull(message = "El precio es requerido")
  private Float price;

  private Float discount;

  // Read-only, computed by ServicioService as price - discount (see
  // com.devluis.utils.Money). Ignored on write — create/update only read
  // name/price/discount off the incoming body. Exposed so any consumer
  // (the "precios/descuentos" admin view, a future mobile screen) gets the
  // correct net price for free instead of re-deriving price-minus-discount
  // itself, which is exactly what happened once already on the Angular side
  // (precios-citas-list.component.ts) with no backend documentation backing
  // it up.
  private BigDecimal netPrice;
}
