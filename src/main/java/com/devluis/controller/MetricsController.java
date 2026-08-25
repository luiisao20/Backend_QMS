package com.devluis.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devluis.dto.EmployeesMetricsDTO;
import com.devluis.dto.EstablishmentsMetricsDTO;
import com.devluis.dto.MetricsSummaryDTO;
import com.devluis.dto.PatientsMetricsDTO;
import com.devluis.dto.TurnsSeriesDTO;
import com.devluis.services.MetricsService;

import lombok.RequiredArgsConstructor;

/**
 * Aggregates for the admin panel's dashboard/metrics screens (dashboard/
 * resumen, dashboard/analytics, metricas/establecimientos,
 * metricas/empleados, metricas/pacientes, reportes/general). Read-only;
 * every number is computed by MetricsService from the existing entities —
 * no new tables.
 *
 * <p>No try/catch here on purpose: business-rule failures (e.g. an invalid
 * date range) are plain RuntimeExceptions, handled uniformly by
 * {@link com.devluis.exception.GlobalExceptionHandler} into a 400 with
 * {"message": ...}, the same pattern already used by StablishmentController,
 * ScheduleController and ServicioController.
 *
 * <p>Authorization: "/api/metrics/**" is restricted to
 * ROLE_DOCTOR/ROLE_EMPLOYEE/ROLE_ADMIN in GlobalConfig (same tier as
 * GET /api/turns and GET /api/patients) — this is staff-only data, not
 * exposed to ROLE_PATIENT.
 */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

  private final MetricsService metricsService;

  @GetMapping("/summary")
  public MetricsSummaryDTO getSummary() {
    return metricsService.getSummary();
  }

  @GetMapping("/turns")
  public TurnsSeriesDTO getTurnsSeries(
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
      @RequestParam(required = false) Long stablishmentId,
      @RequestParam(required = false) Long serviceId) {
    return metricsService.getTurnsSeries(from, to, stablishmentId, serviceId);
  }

  @GetMapping("/establishments")
  public EstablishmentsMetricsDTO getEstablishmentMetrics(
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
    return metricsService.getEstablishmentMetrics(from, to);
  }

  @GetMapping("/employees")
  public EmployeesMetricsDTO getEmployeesMetrics(
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
    return metricsService.getEmployeesMetrics(from, to);
  }

  @GetMapping("/patients")
  public PatientsMetricsDTO getPatientsMetrics(
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
    return metricsService.getPatientsMetrics(from, to);
  }
}
