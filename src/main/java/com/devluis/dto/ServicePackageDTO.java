package com.devluis.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class ServicePackageDTO {
  private Long id;

  @NotBlank(message = "El nombre del paquete es requerido")
  private String name;

  private String description;

  @NotNull(message = "El precio del paquete es obligatorio")
  @DecimalMin(value = "0.0", message = "El precio no puede ser negativo")
  private BigDecimal price;

  // @Valid here (unlike the shallow id-only nested DTOs elsewhere, e.g.
  // CoveragePlanDTO.insurer) on purpose: each item's `quantity` is real,
  // user-editable data that must itself be validated, not just an id
  // reference to resolve.
  @NotEmpty(message = "El paquete debe tener al menos un servicio")
  @Valid
  private List<PackageItemDTO> items;

  // Read-only, computed by ServicePackageService: sum of each item's
  // Servicio net price (price - discount) times its quantity — "what the
  // items would cost bought separately today". NOT validated against
  // `price` (see ServicePackage's docblock: deliberately unenforced).
  private BigDecimal itemsTotal;

  // Read-only, computed: itemsTotal - price. Can be negative if price is
  // set above itemsTotal — surfaced as-is, not clamped, so an admin pricing
  // mistake stays visible instead of being hidden.
  private BigDecimal savings;

  private OffsetDateTime createdAt;
}
