package com.devluis.repository;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.TimeOff;
import com.devluis.types.TimeOffKind;

public interface TimeOffRepository extends JpaRepository<TimeOff, Long> {

  Page<TimeOff> findByDoctorUuid(UUID doctorUuid, Pageable pageable);

  Page<TimeOff> findByKind(TimeOffKind kind, Pageable pageable);

  Page<TimeOff> findByDoctorUuidAndKind(UUID doctorUuid, TimeOffKind kind, Pageable pageable);

  // Delete guard for BlockReasonService — see HolidayRepository#existsByReasonId.
  boolean existsByReasonId(Long reasonId);

  // Generation-time check used by ScheduleService.generateSchedules: is
  // `date` inside [startDate, endDate] for this doctor? Called with the same
  // date for both bounds (date, date). UNVERIFIED AGAINST A REAL DATABASE —
  // Spring Data derives the JPQL from this method name; see the apply report.
  boolean existsByDoctorUuidAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
      UUID doctorUuid, LocalDate date, LocalDate sameDate);
}
