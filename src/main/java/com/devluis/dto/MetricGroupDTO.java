package com.devluis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Una barra de un grafico agrupado: quien, como se llama, y cuantos.
 *
 * `id` es String y no Long ni UUID a proposito: los agrupamientos del panel
 * tienen claves de tipos distintos — un establecimiento es Long, un doctor es
 * UUID — y un solo DTO con la clave ya serializada evita tres DTOs identicos.
 * El cliente lo usa para navegar al detalle, no para hacer cuentas.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MetricGroupDTO {
  private String id;
  private String label;
  private Long total;
}
