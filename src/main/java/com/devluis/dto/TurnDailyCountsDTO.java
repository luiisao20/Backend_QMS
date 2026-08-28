package com.devluis.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Conteo de turnos de UN dia, agrupado por sede y por servicio.
 *
 * Lo consumen las tarjetas del panel de turnos: el paso 1 elige sede y el paso
 * 3 elige servicio, y hasta ahora las dos pantallas mostraban tarjetas sin un
 * solo numero — habia que entrar a cada una para descubrir si tenia trabajo.
 *
 * Las DOS agrupaciones viajan en la MISMA respuesta a proposito. Son dos
 * pedidos que ocurren con un clic de diferencia sobre la misma fecha; partirlo
 * en dos endpoints obliga al panel a un segundo viaje justo cuando el operador
 * ya eligio sede y esta esperando ver los servicios.
 *
 * `byService` NO viene filtrado por sede: un servicio se atiende en varias
 * sedes y el corte por sede lo hace el cliente, que ya sabe cual eligio. Es la
 * misma respuesta para las seis tarjetas del paso 1, asi que se cachea sola.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TurnDailyCountsDTO {

  private LocalDate date;

  private List<ScopeCount> byStablishment;

  private List<ScopeCount> byService;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class ScopeCount {

    /** Id de la sede o del servicio, segun la lista en la que aparece. */
    private Long id;

    /** Todos los turnos del dia, cancelados incluidos. */
    private Long total;

    /**
     * Solo TURN_PENDING.
     *
     * Es EXACTAMENTE la etiqueta "Pendiente" que el panel ya pinta en la lista,
     * y no "todo lo que falta atender". Si el numero de la tarjeta no se puede
     * verificar contando las filas de la lista, la tarjeta deja de ser un dato
     * y pasa a ser una opinion.
     *
     * Ojo con lo que NO cuenta: un turno en TURN_WAITNG ya hizo check-in y
     * espera en sala, y uno en TURN_IN_TREATMENT esta siendo atendido. Ninguno
     * de los dos es "pendiente" en el vocabulario de esta aplicacion.
     */
    private Long pending;
  }
}
