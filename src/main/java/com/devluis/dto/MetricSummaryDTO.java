package com.devluis.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * El resumen del Dashboard en una sola respuesta.
 *
 * UN ENDPOINT Y NO OCHO. La pantalla muestra ocho números juntos; servirlos por
 * separado obliga al panel a disparar ocho requests para pintar una fila de
 * tarjetas, y a que esas tarjetas aparezcan de a una.
 *
 * `from` y `to` viajan de vuelta en la respuesta a propósito: el rango puede
 * venir vacío en la petición, y sin esto la pantalla no tiene forma de rotular
 * honestamente sobre qué periodo son los números que está mostrando.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MetricSummaryDTO {

  /** Rango efectivamente aplicado. En null significa "sin límite". */
  private LocalDate from;
  private LocalDate to;

  // Totales del sistema. No dependen del rango: son cuántos hay hoy.
  private Long totalPatients;
  private Long totalDoctors;
  private Long totalOperators;
  private Long totalStablishments;
  private Long totalServices;

  // Turnos dentro del rango.
  private Long turnsTotal;
  private Long turnsPending;
  private Long turnsTreated;
  private Long turnsCancelled;

  /** Pacientes DISTINTOS con al menos un turno en el rango. */
  private Long activePatients;

  /** Cupos libres en el rango — la capacidad que quedó sin usar. */
  private Long schedulesFree;

  /** Turnos del día de hoy, sin importar el rango consultado. */
  private Long turnsToday;

  /** Serie diaria de turnos dentro del rango, para el gráfico de Analytics. */
  private List<MetricPointDTO> turnsByDay;
}
