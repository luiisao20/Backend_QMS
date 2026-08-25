package com.devluis.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.entity.Encounter;

public interface EncounterRepository extends JpaRepository<Encounter, Long> {

  // Enforces the 1:1 Turn<->Encounter rule at the service layer (see
  // EncounterService#create) in addition to the DB-level unique constraint
  // on turn_id.
  boolean existsByTurnId(Long turnId);

  // "historial-clinico" / "/me": every encounter for a patient, admin tier
  // (no doctor filter). UNVERIFIED AGAINST A REAL DATABASE — see apply report.
  @Query("SELECT e FROM Encounter e WHERE e.turn.patient.uuid = :patientUuid")
  Page<Encounter> findByPatientUuid(@Param("patientUuid") UUID patientUuid, Pageable pageable);

  // Same list, scoped to encounters where the given doctor was the treating
  // doctor — used when the caller is ROLE_DOCTOR (see
  // ClinicalAccessGuard#resolveDoctorFilter). UNVERIFIED AGAINST A REAL
  // DATABASE — see apply report.
  @Query("SELECT e FROM Encounter e WHERE e.turn.patient.uuid = :patientUuid " +
      "AND e.turn.schedule.doctor.uuid = :doctorUuid")
  Page<Encounter> findByPatientUuidAndDoctorUuid(
      @Param("patientUuid") UUID patientUuid, @Param("doctorUuid") UUID doctorUuid, Pageable pageable);
}
