package com.devluis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.devluis.dto.DateStatusCountRow;
import com.devluis.dto.LongStatusCountRow;
import com.devluis.dto.StatusCountRow;
import com.devluis.dto.UuidCountRow;
import com.devluis.dto.UuidStatusCountRow;
import com.devluis.entity.Turn;
import com.devluis.types.TurnStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TurnRepository extends JpaRepository<Turn, Long>, JpaSpecificationExecutor<Turn> {

  @Query("SELECT COUNT(t) FROM Turn t WHERE t.schedule.service.id = :serviceId AND t.schedule.date = :date")
  Long countTurnsByServiceAndDate(@Param("serviceId") Long serviceId, @Param("date") LocalDate date);

  // --- Metrics aggregates (MetricsController / MetricsService) -------------
  // All grouped/counted in the database via GROUP BY + COUNT; MetricsService
  // only reshapes these already-small result sets (bounded by
  // days/statuses/establishments/doctors/operators, never by turn volume).
  // UNVERIFIED AGAINST A REAL DATABASE — see the apply report.

  @Query("SELECT new com.devluis.dto.StatusCountRow(t.status, COUNT(t)) FROM Turn t " +
      "WHERE t.schedule.date = :date GROUP BY t.status")
  List<StatusCountRow> countByStatusForDate(@Param("date") LocalDate date);

  @Query("SELECT new com.devluis.dto.StatusCountRow(t.status, COUNT(t)) FROM Turn t " +
      "WHERE t.schedule.date BETWEEN :from AND :to GROUP BY t.status")
  List<StatusCountRow> countByStatusInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("SELECT new com.devluis.dto.DateStatusCountRow(t.schedule.date, t.status, COUNT(t)) FROM Turn t " +
      "WHERE t.schedule.date BETWEEN :from AND :to " +
      "AND (:stablishmentId IS NULL OR t.schedule.stablishment.id = :stablishmentId) " +
      "AND (:serviceId IS NULL OR t.schedule.service.id = :serviceId) " +
      "GROUP BY t.schedule.date, t.status " +
      "ORDER BY t.schedule.date ASC")
  List<DateStatusCountRow> countByDayAndStatus(
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("stablishmentId") Long stablishmentId,
      @Param("serviceId") Long serviceId);

  @Query("SELECT new com.devluis.dto.LongStatusCountRow(t.schedule.stablishment.id, t.status, COUNT(t)) FROM Turn t " +
      "WHERE t.schedule.date BETWEEN :from AND :to " +
      "GROUP BY t.schedule.stablishment.id, t.status")
  List<LongStatusCountRow> countByStablishmentAndStatusInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("SELECT new com.devluis.dto.UuidStatusCountRow(t.schedule.doctor.uuid, t.status, COUNT(t)) FROM Turn t " +
      "WHERE t.schedule.doctor IS NOT NULL AND t.schedule.date BETWEEN :from AND :to " +
      "GROUP BY t.schedule.doctor.uuid, t.status")
  List<UuidStatusCountRow> countByDoctorAndStatusInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

  // "No-show" has no dedicated TurnStatus — it is derived as: still
  // TURN_PENDING (never checked in) with a schedule date that has already
  // passed. Callers pass `today` and `status` explicitly rather than
  // hardcoding them here, so the query stays a plain, testable-by-mock
  // "count by doctor with this status before this date" primitive.
  @Query("SELECT new com.devluis.dto.UuidCountRow(t.schedule.doctor.uuid, COUNT(t)) FROM Turn t " +
      "WHERE t.schedule.doctor IS NOT NULL AND t.schedule.date BETWEEN :from AND :to " +
      "AND t.schedule.date < :today AND t.status = :status " +
      "GROUP BY t.schedule.doctor.uuid")
  List<UuidCountRow> countByDoctorWithStatusBeforeDate(
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("today") LocalDate today,
      @Param("status") TurnStatus status);

  @Query("SELECT new com.devluis.dto.UuidStatusCountRow(t.operator.uuid, t.status, COUNT(t)) FROM Turn t " +
      "WHERE t.operator IS NOT NULL AND t.schedule.date BETWEEN :from AND :to " +
      "GROUP BY t.operator.uuid, t.status")
  List<UuidStatusCountRow> countByOperatorAndStatusInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

  // Guards for the delete-cascade fix: a Turn must never be destroyed as a
  // side effect of deleting the Schedule/Stablishment/Servicio/Doctor it is
  // booked under. Callers check these BEFORE deleting; see
  // ScheduleService.delete, StablishmentService.delete, ServicioService.delete
  // and DoctorService.deleteDoctor.
  boolean existsByScheduleId(Long scheduleId);

  boolean existsByScheduleStablishmentId(Long stablishmentId);

  boolean existsByScheduleServiceId(Long serviceId);

  boolean existsByScheduleDoctorUuid(UUID doctorUuid);

  // Same guarantee for the two remaining owners of a Turn: deleting the
  // Operator who attended it, or the Patient who booked it, must not destroy
  // the turn record. See OperatorService.deleteOperator.
  boolean existsByOperatorUuid(UUID operatorUuid);

  boolean existsByPatientUuid(UUID patientUuid);

  // TASK 1 concurrency fix: is this schedule still legitimately claimed by
  // some OTHER, still-active (non-cancelled) turn? Used before releasing a
  // schedule back to STATUS_FREE after a cancel/reassign, so that a schedule
  // is never freed while another turn still holds it — including turns left
  // over from before this fix shipped, when the same schedule could be
  // double-booked. See TurnService.releaseScheduleIfUnclaimed.
  boolean existsByScheduleIdAndStatusNotAndIdNot(Long scheduleId, TurnStatus status, Long id);

}
