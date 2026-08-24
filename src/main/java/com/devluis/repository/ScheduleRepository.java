package com.devluis.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.devluis.entity.Schedule;

public interface ScheduleRepository extends JpaRepository<Schedule, Long>, JpaSpecificationExecutor<Schedule> {

  boolean existsByDoctorUuidAndDateAndHour(UUID doctorUuid, LocalDate date, LocalTime hour);

  boolean existsByDoctorUuidAndDateAndHourAndIdNot(UUID doctorUuid, LocalDate date, LocalTime hour, Long id);

  boolean existsByServiceIdAndStablishmentIdAndDateAndHour(Long serviceId, Long stablishmentId, LocalDate date, LocalTime hour);

  boolean existsByServiceIdAndStablishmentIdAndDoctorUuidAndDateAndHour(Long serviceId, Long stablishmentId, UUID doctorUuid, LocalDate date, LocalTime hour);
}
