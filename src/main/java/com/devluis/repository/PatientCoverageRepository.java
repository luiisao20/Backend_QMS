package com.devluis.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.PatientCoverage;

public interface PatientCoverageRepository extends JpaRepository<PatientCoverage, Long> {

  // Backs both GET /api/patient-coverages/me and the staff
  // GET /api/patients/{patientId}/coverages screen — bounded, small
  // per-patient history, same "List, not Page" precedent as
  // ServicioService#getMyServices. UNVERIFIED AGAINST A REAL DATABASE — see
  // apply report.
  List<PatientCoverage> findByPatientUuidOrderByValidFromDesc(UUID patientUuid);

  // Used to enforce "at most one active coverage per patient" — see
  // PatientCoverageService#deactivateOtherActiveCoverages. UNVERIFIED
  // AGAINST A REAL DATABASE.
  List<PatientCoverage> findByPatientUuidAndActiveTrueAndIdNot(UUID patientUuid, Long id);

  // Pricing quote source of truth: the ONE currently active coverage, if any.
  // `findFirstBy...` (not `findBy...`) on purpose: defends against a
  // corrupted/manually-edited row breaking the "at most one active"
  // invariant this service otherwise enforces, instead of throwing
  // NonUniqueResultException. UNVERIFIED AGAINST A REAL DATABASE.
  Optional<PatientCoverage> findFirstByPatientUuidAndActiveTrue(UUID patientUuid);

  // Delete guard for CoveragePlanService. UNVERIFIED AGAINST A REAL DATABASE.
  boolean existsByPlanId(Long planId);
}
