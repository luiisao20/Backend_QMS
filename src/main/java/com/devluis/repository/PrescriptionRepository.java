package com.devluis.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.entity.Prescription;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

  // "pacientes/recetas" / "/me": every prescription for a patient, admin
  // tier (no doctor filter). UNVERIFIED AGAINST A REAL DATABASE — see apply
  // report.
  @Query("SELECT p FROM Prescription p WHERE p.encounter.turn.patient.uuid = :patientUuid")
  Page<Prescription> findByPatientUuid(@Param("patientUuid") UUID patientUuid, Pageable pageable);

  // Same list, scoped to prescriptions issued during an encounter this
  // doctor treated (see ClinicalAccessGuard#resolveDoctorFilter). UNVERIFIED
  // AGAINST A REAL DATABASE — see apply report.
  @Query("SELECT p FROM Prescription p WHERE p.encounter.turn.patient.uuid = :patientUuid " +
      "AND p.encounter.turn.schedule.doctor.uuid = :doctorUuid")
  Page<Prescription> findByPatientUuidAndDoctorUuid(
      @Param("patientUuid") UUID patientUuid, @Param("doctorUuid") UUID doctorUuid, Pageable pageable);
}
