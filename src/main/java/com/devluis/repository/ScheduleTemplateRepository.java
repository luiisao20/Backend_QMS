package com.devluis.repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.entity.ScheduleTemplate;

// All queries below are UNVERIFIED against a real database — no DATABASE_URL
// is configured in this environment.
public interface ScheduleTemplateRepository
    extends JpaRepository<ScheduleTemplate, Long>, JpaSpecificationExecutor<ScheduleTemplate> {

  // Doctor-scoped overlap guard: a doctor cannot have two templates on the
  // same weekday with overlapping [startTime, endTime) windows AND
  // overlapping validity windows, regardless of establishment/service —
  // mirrors how ScheduleRepository#existsByDoctorUuidAndDateAndHour already
  // treats "is this doctor free at this instant" as a GLOBAL, not
  // per-establishment, question. Time overlap uses STRICT inequalities
  // (start < otherEnd AND otherStart < end) so back-to-back templates
  // (08:00-12:00 then 12:00-16:00) are NOT flagged — unlike Promotion's
  // closed-date-range check, a shared boundary INSTANT is not a real overlap
  // for a time-of-day window. Validity-window overlap treats a NULL
  // validUntil (either side) as "runs forever", matching ScheduleTemplate's
  // own "null = open-ended" contract.
  @Query("SELECT COUNT(t) > 0 FROM ScheduleTemplate t "
      + "WHERE t.doctor.uuid = :doctorUuid "
      + "AND t.dayOfWeek = :dayOfWeek "
      + "AND (:excludeId IS NULL OR t.id <> :excludeId) "
      + "AND t.startTime < :endTime AND :startTime < t.endTime "
      + "AND (:validUntil IS NULL OR t.validFrom <= :validUntil) "
      + "AND (t.validUntil IS NULL OR t.validUntil >= :validFrom)")
  boolean existsOverlappingForDoctor(
      @Param("doctorUuid") UUID doctorUuid,
      @Param("dayOfWeek") DayOfWeek dayOfWeek,
      @Param("startTime") LocalTime startTime,
      @Param("endTime") LocalTime endTime,
      @Param("validFrom") LocalDate validFrom,
      @Param("validUntil") LocalDate validUntil,
      @Param("excludeId") Long excludeId);

  // Pool overlap guard (doctor IS NULL): same rule, scoped instead by
  // establishment+service — mirrors
  // ScheduleRepository#existsByServiceIdAndStablishmentIdAndDateAndHour's
  // establishment+service scoping for doctor-less slots.
  @Query("SELECT COUNT(t) > 0 FROM ScheduleTemplate t "
      + "WHERE t.doctor IS NULL "
      + "AND t.stablishment.id = :stablishmentId "
      + "AND t.servicio.id = :serviceId "
      + "AND t.dayOfWeek = :dayOfWeek "
      + "AND (:excludeId IS NULL OR t.id <> :excludeId) "
      + "AND t.startTime < :endTime AND :startTime < t.endTime "
      + "AND (:validUntil IS NULL OR t.validFrom <= :validUntil) "
      + "AND (t.validUntil IS NULL OR t.validUntil >= :validFrom)")
  boolean existsOverlappingForPool(
      @Param("stablishmentId") Long stablishmentId,
      @Param("serviceId") Long serviceId,
      @Param("dayOfWeek") DayOfWeek dayOfWeek,
      @Param("startTime") LocalTime startTime,
      @Param("endTime") LocalTime endTime,
      @Param("validFrom") LocalDate validFrom,
      @Param("validUntil") LocalDate validUntil,
      @Param("excludeId") Long excludeId);

  // Generation-time lookup used by
  // ScheduleService#generateSchedulesFromTemplates: the ONE applicable
  // template (if any) for this scope/weekday/date. Safe to assume at most
  // one row matches — see existsOverlappingForDoctor/Pool above, which
  // reject any create/update that would produce a second match.
  @Query("SELECT t FROM ScheduleTemplate t WHERE t.stablishment.id = :stablishmentId "
      + "AND t.servicio.id = :serviceId "
      + "AND ((:doctorUuid IS NULL AND t.doctor IS NULL) OR t.doctor.uuid = :doctorUuid) "
      + "AND t.dayOfWeek = :dayOfWeek "
      + "AND t.validFrom <= :date AND (t.validUntil IS NULL OR t.validUntil >= :date)")
  Optional<ScheduleTemplate> findApplicable(
      @Param("stablishmentId") Long stablishmentId,
      @Param("serviceId") Long serviceId,
      @Param("doctorUuid") UUID doctorUuid,
      @Param("dayOfWeek") DayOfWeek dayOfWeek,
      @Param("date") LocalDate date);
}
