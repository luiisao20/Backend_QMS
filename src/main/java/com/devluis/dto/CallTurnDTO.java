package com.devluis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo del llamado de un turno (PUT /api/turns/{id}/in-treatment).
 *
 * Todo opcional a proposito: el cuerpo entero puede faltar. Los clientes que ya
 * mandan {} siguen funcionando y el turno sale por el consultorio que traia el
 * cupo, que es el que definio un admin en la plantilla.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CallTurnDTO {
  /** null = usar el consultorio del cupo. No es "sin consultorio". */
  private Long consultorioId;
}
