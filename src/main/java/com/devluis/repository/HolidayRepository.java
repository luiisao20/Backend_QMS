package com.devluis.repository;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.entity.Holiday;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

  Page<Holiday> findByStablishmentId(Long stablishmentId, Pageable pageable);

  // Delete guard for BlockReasonService — mirrors the
  // TurnRepository.existsByScheduleStablishmentId family used by
  // StablishmentService/ScheduleService: a catalog row cannot be removed
  // while something still references it.
  boolean existsByReasonId(Long reasonId);

  // Generation-time check used by ScheduleService.generateSchedules: does a
  // Holiday cover this date for this establishment, either because it names
  // that establishment specifically or because it is global (stablishment
  // IS NULL)? UNVERIFIED AGAINST A REAL DATABASE — see the apply report.
  @Query("SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END FROM Holiday h " +
      "WHERE h.date = :date AND (h.stablishment IS NULL OR h.stablishment.id = :stablishmentId)")
  boolean existsApplicableHoliday(@Param("date") LocalDate date, @Param("stablishmentId") Long stablishmentId);
}
