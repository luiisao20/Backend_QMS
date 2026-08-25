package com.devluis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.devluis.entity.Turn;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TurnRepository extends JpaRepository<Turn, Long>, JpaSpecificationExecutor<Turn> {

  @Query("SELECT COUNT(t) FROM Turn t WHERE t.schedule.service.id = :serviceId AND t.schedule.date = :date")
  Long countTurnsByServiceAndDate(@Param("serviceId") Long serviceId, @Param("date") LocalDate date);

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

}
