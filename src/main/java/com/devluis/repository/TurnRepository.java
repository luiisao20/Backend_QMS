package com.devluis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.Turn;
import com.devluis.types.TurnStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TurnRepository extends JpaRepository<Turn, Long> {

  @Query("SELECT COUNT(t) FROM Turn t WHERE t.schedule.service.id = :serviceId AND t.schedule.date = :date")
  Long countTurnsByServiceAndDate(@Param("serviceId") Long serviceId, @Param("date") LocalDate date);

  @Query("SELECT t FROM Turn t WHERE t.patient.uuid = :patientUuid " +
         "AND (:status IS NULL OR t.status = :status) " +
         "AND (:fromDate IS NULL OR t.schedule.date >= :fromDate) " +
         "AND (:toDate IS NULL OR t.schedule.date <= :toDate) " +
         "ORDER BY t.schedule.date DESC, t.order ASC")
  org.springframework.data.domain.Page<Turn> findTurnsForPatient(
      @Param("patientUuid") java.util.UUID patientUuid,
      @Param("status") com.devluis.types.TurnStatus status,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate,
      org.springframework.data.domain.Pageable pageable);

  // ============================================================
  // AGREGADOS PARA EL PANEL — Dashboard, Métricas y Reportes
  //
  // Todo se agrupa en la BASE DE DATOS, no en Java. La alternativa —
  // `findAll()` y agrupar con streams — trae la tabla entera de turnos a
  // memoria para devolver diez números, y es la clase de endpoint que anda en
  // desarrollo y se cae el día que hay datos reales.
  //
  // El rango es opcional en todas: en null no filtra, el mismo patrón
  // `:x IS NULL OR ...` que ya usa `findTurnsForPatient` acá arriba.
  // ============================================================

  @Query("SELECT COUNT(t) FROM Turn t WHERE " +
      "(:from IS NULL OR t.schedule.date >= :from) AND " +
      "(:to IS NULL OR t.schedule.date <= :to)")
  long countInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("SELECT COUNT(t) FROM Turn t WHERE t.status = :status AND " +
      "(:from IS NULL OR t.schedule.date >= :from) AND " +
      "(:to IS NULL OR t.schedule.date <= :to)")
  long countByStatusInRange(
      @Param("status") TurnStatus status,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);

  @Query("SELECT t.schedule.date AS date, COUNT(t) AS total FROM Turn t WHERE " +
      "(:from IS NULL OR t.schedule.date >= :from) AND " +
      "(:to IS NULL OR t.schedule.date <= :to) " +
      "GROUP BY t.schedule.date ORDER BY t.schedule.date ASC")
  List<DayCount> countByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("SELECT t.status AS status, COUNT(t) AS total FROM Turn t WHERE " +
      "(:from IS NULL OR t.schedule.date >= :from) AND " +
      "(:to IS NULL OR t.schedule.date <= :to) " +
      "GROUP BY t.status")
  List<StatusCount> countByStatus(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("SELECT t.schedule.stablishment.id AS id, t.schedule.stablishment.name AS label, " +
      "COUNT(t) AS total FROM Turn t WHERE t.schedule.stablishment IS NOT NULL AND " +
      "(:from IS NULL OR t.schedule.date >= :from) AND " +
      "(:to IS NULL OR t.schedule.date <= :to) " +
      "GROUP BY t.schedule.stablishment.id, t.schedule.stablishment.name " +
      "ORDER BY COUNT(t) DESC")
  List<StablishmentCount> countByStablishment(@Param("from") LocalDate from, @Param("to") LocalDate to);

  /**
   * Agrupado por el UUID del doctor y no por su especialidad, que es lo que a
   * primera vista pediría la pantalla "Métricas → Empleados". `Doctor.speciality`
   * es texto libre, así que agrupar por ahí cuenta «Cardiología» y «cardiologia»
   * como dos. Cuando `speciality_id` esté backfilleado se puede agrupar por el
   * catálogo; hasta entonces, agrupar por doctor es el número que no miente.
   */
  @Query("SELECT t.schedule.doctor.uuid AS id, " +
      "CONCAT(t.schedule.doctor.firstName, ' ', t.schedule.doctor.lastName) AS label, " +
      "COUNT(t) AS total FROM Turn t WHERE t.schedule.doctor IS NOT NULL AND " +
      "(:from IS NULL OR t.schedule.date >= :from) AND " +
      "(:to IS NULL OR t.schedule.date <= :to) " +
      "GROUP BY t.schedule.doctor.uuid, t.schedule.doctor.firstName, t.schedule.doctor.lastName " +
      "ORDER BY COUNT(t) DESC")
  List<DoctorCount> countByDoctor(@Param("from") LocalDate from, @Param("to") LocalDate to);

  /** Cuántos pacientes distintos tuvieron al menos un turno en el rango. */
  @Query("SELECT COUNT(DISTINCT t.patient.uuid) FROM Turn t WHERE t.patient IS NOT NULL AND " +
      "(:from IS NULL OR t.schedule.date >= :from) AND " +
      "(:to IS NULL OR t.schedule.date <= :to)")
  long countDistinctPatientsInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

  interface DayCount {
    LocalDate getDate();

    Long getTotal();
  }

  interface StatusCount {
    TurnStatus getStatus();

    Long getTotal();
  }

  interface StablishmentCount {
    Long getId();

    String getLabel();

    Long getTotal();
  }

  interface DoctorCount {
    UUID getId();

    String getLabel();

    Long getTotal();
  }
}
