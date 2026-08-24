package com.devluis.controller;

import com.devluis.dto.MetricGroupDTO;
import com.devluis.dto.MetricPointDTO;
import com.devluis.dto.MetricSummaryDTO;
import com.devluis.services.MetricsService;
import lombok.Data;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Agregados de solo lectura para el panel administrativo.
 *
 * Cubre seis destinos sin una sola tabla nueva: Dashboard (Resumen general y
 * Analytics), Métricas (Establecimientos, Empleados, Pacientes) y Reportes →
 * General, que es el mismo motor.
 *
 * `from` y `to` son OPCIONALES en todos. Sin rango devuelve el total histórico,
 * que es lo que quiere una tarjeta de "total de pacientes"; con rango, lo que
 * quiere un gráfico.
 *
 * Nada acá escribe. Si alguna vez hace falta un endpoint de métricas que
 * modifique estado, no va en este controller.
 */
@RestController
@RequestMapping("/api/metrics")
@Data
public class MetricsController {

  private final MetricsService metricsService;

  /** Dashboard → Resumen general: los ocho números y la serie diaria, juntos. */
  @GetMapping("/summary")
  public ResponseEntity<MetricSummaryDTO> summary(
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(metricsService.getSummary(from, to));
  }

  /** Dashboard → Analytics: turnos por día. */
  @GetMapping("/turns-by-day")
  public ResponseEntity<List<MetricPointDTO>> turnsByDay(
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(metricsService.getTurnsByDay(from, to));
  }

  @GetMapping("/turns-by-status")
  public ResponseEntity<List<MetricGroupDTO>> turnsByStatus(
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(metricsService.getTurnsByStatus(from, to));
  }

  /** Métricas → Establecimientos. */
  @GetMapping("/turns-by-stablishment")
  public ResponseEntity<List<MetricGroupDTO>> turnsByStablishment(
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(metricsService.getTurnsByStablishment(from, to));
  }

  /**
   * Métricas → Empleados, agrupado por DOCTOR y no por especialidad.
   * `Doctor.speciality` es texto libre y agrupar por ahí cuenta «Cardiología» y
   * «cardiologia» como dos — ver el doc de `TurnRepository.countByDoctor`.
   */
  @GetMapping("/turns-by-doctor")
  public ResponseEntity<List<MetricGroupDTO>> turnsByDoctor(
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(metricsService.getTurnsByDoctor(from, to));
  }
}
