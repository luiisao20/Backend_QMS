package com.devluis.services;

import com.devluis.dto.MetricGroupDTO;
import com.devluis.dto.MetricPointDTO;
import com.devluis.dto.MetricSummaryDTO;
import com.devluis.repository.DoctorRepository;
import com.devluis.repository.OperatorRepository;
import com.devluis.repository.PatientRepository;
import com.devluis.repository.ScheduleRepository;
import com.devluis.repository.ServiceRepository;
import com.devluis.repository.StablishmentRepository;
import com.devluis.repository.TurnRepository;
import com.devluis.types.ScheduleStatus;
import com.devluis.types.TurnStatus;
import lombok.Data;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Los agregados del panel: Dashboard, Métricas y Reportes → General.
 *
 * NO GUARDA NADA. Solo lee y suma sobre las tablas que ya existen, que es lo
 * que hace que seis destinos del panel se puedan construir sin una sola tabla
 * nueva.
 *
 * TODO SE AGRUPA EN LA BASE, no en Java. Ver el bloque de consultas en
 * `TurnRepository`: traer los turnos a memoria para contarlos anda en
 * desarrollo y se cae con datos reales.
 *
 * NO INVENTA NÚMEROS. Si una métrica no se puede calcular con el esquema actual
 * no aparece acá — el panel ya tiene un placeholder honesto para eso, y un
 * número inventado enseña a desconfiar de todos los demás.
 */
@Service
@Data
public class MetricsService {

  private final TurnRepository turnRepository;
  private final ScheduleRepository scheduleRepository;
  private final PatientRepository patientRepository;
  private final DoctorRepository doctorRepository;
  private final OperatorRepository operatorRepository;
  private final StablishmentRepository stablishmentRepository;
  private final ServiceRepository serviceRepository;

  public MetricSummaryDTO getSummary(LocalDate from, LocalDate to) {
    LocalDate today = LocalDate.now();

    return MetricSummaryDTO.builder()
        .from(from)
        .to(to)
        .totalPatients(patientRepository.count())
        .totalDoctors(doctorRepository.count())
        .totalOperators(operatorRepository.count())
        .totalStablishments(stablishmentRepository.count())
        .totalServices(serviceRepository.count())
        .turnsTotal(turnRepository.countInRange(from, to))
        .turnsPending(turnRepository.countByStatusInRange(TurnStatus.TURN_PENDING, from, to))
        .turnsTreated(turnRepository.countByStatusInRange(TurnStatus.TURN_TREATED, from, to))
        .turnsCancelled(turnRepository.countByStatusInRange(TurnStatus.TURN_CANCELLED, from, to))
        .activePatients(turnRepository.countDistinctPatientsInRange(from, to))
        .schedulesFree(scheduleRepository.countByStatusInRange(ScheduleStatus.STATUS_FREE, from, to))
        .turnsToday(turnRepository.countInRange(today, today))
        .turnsByDay(getTurnsByDay(from, to))
        .build();
  }

  /** Serie diaria. Solo trae los días QUE TIENEN turnos — rellenar los ceros es
   *  trabajo del gráfico, que es el único que sabe qué granularidad dibuja. */
  public List<MetricPointDTO> getTurnsByDay(LocalDate from, LocalDate to) {
    return turnRepository.countByDay(from, to).stream()
        .map(row -> MetricPointDTO.builder().date(row.getDate()).total(row.getTotal()).build())
        .collect(Collectors.toList());
  }

  public List<MetricGroupDTO> getTurnsByStatus(LocalDate from, LocalDate to) {
    return turnRepository.countByStatus(from, to).stream()
        .map(row -> MetricGroupDTO.builder()
            .id(row.getStatus().name())
            .label(row.getStatus().name())
            .total(row.getTotal())
            .build())
        .collect(Collectors.toList());
  }

  public List<MetricGroupDTO> getTurnsByStablishment(LocalDate from, LocalDate to) {
    return turnRepository.countByStablishment(from, to).stream()
        .map(row -> MetricGroupDTO.builder()
            .id(String.valueOf(row.getId()))
            .label(row.getLabel())
            .total(row.getTotal())
            .build())
        .collect(Collectors.toList());
  }

  public List<MetricGroupDTO> getTurnsByDoctor(LocalDate from, LocalDate to) {
    return turnRepository.countByDoctor(from, to).stream()
        .map(row -> MetricGroupDTO.builder()
            .id(row.getId().toString())
            .label(row.getLabel())
            .total(row.getTotal())
            .build())
        .collect(Collectors.toList());
  }
}
