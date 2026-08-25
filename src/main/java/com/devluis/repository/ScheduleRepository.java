package com.devluis.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.dto.LongCountRow;
import com.devluis.entity.Schedule;
import com.devluis.types.TurnStatus;

public interface ScheduleRepository extends JpaRepository<Schedule, Long>, JpaSpecificationExecutor<Schedule> {

  boolean existsByDoctorUuidAndDateAndHour(UUID doctorUuid, LocalDate date, LocalTime hour);

  boolean existsByDoctorUuidAndDateAndHourAndIdNot(UUID doctorUuid, LocalDate date, LocalTime hour, Long id);

  boolean existsByServiceIdAndStablishmentIdAndDateAndHour(Long serviceId, Long stablishmentId, LocalDate date, LocalTime hour);

  boolean existsByServiceIdAndStablishmentIdAndDoctorUuidAndDateAndHour(Long serviceId, Long stablishmentId, UUID doctorUuid, LocalDate date, LocalTime hour);

  // --- "Bloqueo de citas" sweep (HolidayService / TimeOffService) ---------
  // Finds pre-existing schedules affected by a newly created/updated Holiday
  // or TimeOff, so ScheduleBlockingSupport can flip the free ones to
  // STATUS_UNAVAILABLE and report the occupied ones as conflicts. There is
  // no FK from Schedule back to Holiday/TimeOff (deliberately — see the
  // apply report on why "undo on delete" is NOT automated), so this always
  // matches by date/establishment/doctor criteria, not by a stored link.
  // UNVERIFIED AGAINST A REAL DATABASE — see the apply report.

  // Global holiday: every schedule on that date, regardless of establishment.
  List<Schedule> findByDate(LocalDate date);

  // Establishment-scoped holiday.
  List<Schedule> findByDateAndStablishmentId(LocalDate date, Long stablishmentId);

  // TimeOff: every schedule for that doctor whose date falls in [start, end].
  List<Schedule> findByDoctorUuidAndDateBetween(UUID doctorUuid, LocalDate startDate, LocalDate endDate);

  // --- Metrics aggregates (MetricsController / MetricsService) -------------
  // UNVERIFIED AGAINST A REAL DATABASE — see the apply report.

  // Total capacity offered by each stablishment in the period, regardless of
  // Schedule.status (see MetricsService for why status is not trustworthy).
  @Query("SELECT new com.devluis.dto.LongCountRow(s.stablishment.id, COUNT(s)) FROM Schedule s " +
      "WHERE s.date BETWEEN :from AND :to GROUP BY s.stablishment.id")
  List<LongCountRow> countTotalSlotsByStablishmentInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

  // Slots actually consumed: a schedule counts as occupied if it has at
  // least one turn that is not cancelled. COUNT(DISTINCT s.id) avoids
  // double-counting a schedule that (due to an existing, unrelated gap in
  // ScheduleService) ended up with more than one turn.
  @Query("SELECT new com.devluis.dto.LongCountRow(s.stablishment.id, COUNT(DISTINCT s.id)) FROM Schedule s " +
      "JOIN s.turns t " +
      "WHERE s.date BETWEEN :from AND :to AND t.status <> :cancelledStatus " +
      "GROUP BY s.stablishment.id")
  List<LongCountRow> countOccupiedSlotsByStablishmentInRange(
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("cancelledStatus") TurnStatus cancelledStatus);
}
