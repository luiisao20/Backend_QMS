package com.devluis.repository;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.entity.Schedule;
import com.devluis.types.ScheduleStatus;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

  /**
   * Búsqueda de disponibilidad. Todo parámetro es opcional y en null no filtra,
   * que es lo que permite servir con una sola consulta a los tres consumidores:
   *
   *   - la app móvil arma sus tres pasos de "Agendar" (médico → día → hora)
   *     preguntando por `doctorId` + `serviceId` + `status=STATUS_FREE`;
   *   - el calendario del panel pide un rango de fechas sin filtrar nada más;
   *   - la pantalla de sala pide una sede y un día.
   *
   * El patrón `:x IS NULL OR ...` es el mismo que ya usa
   * `TurnRepository.findTurnsForPatient`, así que se lee igual que lo que hay.
   *
   * ORDENADO POR FECHA Y HORA, no por id. Una lista de cupos que llega
   * desordenada obliga a cada cliente a reordenarla, y el paginado la parte en
   * páginas que no significan nada.
   */
  @Query("SELECT s FROM Schedule s WHERE " +
      "(:doctorId IS NULL OR s.doctor.uuid = :doctorId) AND " +
      "(:serviceId IS NULL OR s.service.id = :serviceId) AND " +
      "(:stablishmentId IS NULL OR s.stablishment.id = :stablishmentId) AND " +
      "(:from IS NULL OR s.date >= :from) AND " +
      "(:to IS NULL OR s.date <= :to) AND " +
      "(:status IS NULL OR s.status = :status) " +
      "ORDER BY s.date ASC, s.hour ASC")
  Page<Schedule> search(
      @Param("doctorId") UUID doctorId,
      @Param("serviceId") Long serviceId,
      @Param("stablishmentId") Long stablishmentId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("status") ScheduleStatus status,
      Pageable pageable);

  /** Cupos por estado en un rango — para el resumen del panel. */
  @Query("SELECT COUNT(s) FROM Schedule s WHERE s.status = :status AND " +
      "(:from IS NULL OR s.date >= :from) AND " +
      "(:to IS NULL OR s.date <= :to)")
  long countByStatusInRange(
      @Param("status") ScheduleStatus status,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);
}
