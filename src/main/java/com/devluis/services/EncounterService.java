package com.devluis.services;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devluis.dto.EncounterDTO;
import com.devluis.entity.Doctor;
import com.devluis.entity.Encounter;
import com.devluis.entity.Schedule;
import com.devluis.entity.Turn;
import com.devluis.repository.EncounterRepository;
import com.devluis.repository.TurnRepository;
import com.devluis.types.ClinicalResourceType;
import com.devluis.types.TurnStatus;

import lombok.Data;

@Service
@Data
public class EncounterService {
  private final EncounterRepository encounterRepository;
  private final TurnRepository turnRepository;
  private final ClinicalAccessGuard clinicalAccessGuard;
  private final ClinicalAccessLogService clinicalAccessLogService;

  @Transactional
  public EncounterDTO create(EncounterDTO dto, Authentication auth) {
    Turn turn = turnRepository.findById(dto.getTurnId())
        .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

    // A cancelled turn never happened clinically and can never be documented.
    // TURN_TREATED is required (not TURN_IN_TREATMENT) so an Encounter always
    // represents a CONCLUDED visit, matching how markAsTreated is the
    // codebase's own "the doctor finished attending the patient" signal.
    if (turn.getStatus() != TurnStatus.TURN_TREATED) {
      throw new RuntimeException("Solo se puede registrar una historia clínica para un turno atendido");
    }

    if (turn.getSchedule() == null || turn.getSchedule().getDoctor() == null) {
      throw new RuntimeException("El turno no tiene un doctor asignado");
    }

    if (encounterRepository.existsByTurnId(turn.getId())) {
      throw new RuntimeException("Ya existe una historia clínica para este turno");
    }

    UUID treatingDoctorUuid = turn.getSchedule().getDoctor().getUuid();
    clinicalAccessGuard.assertCanAccessEncounter(auth, treatingDoctorUuid);

    Encounter encounter = Encounter.builder()
        .turn(turn)
        .reasonForVisit(dto.getReasonForVisit())
        .clinicalNotes(dto.getClinicalNotes())
        .diagnosis(dto.getDiagnosis())
        .build();

    Encounter saved = encounterRepository.save(encounter);
    return mapToDTO(saved);
  }

  public EncounterDTO getById(Long id, Authentication auth) {
    Encounter encounter = findByIdOrThrow(id);
    clinicalAccessGuard.assertCanAccessEncounter(auth, resolveTreatingDoctorUuid(encounter));

    clinicalAccessLogService.record(
        resolvePatientUuid(encounter), auth, ClinicalResourceType.ENCOUNTER, encounter.getId());

    return mapToDTO(encounter);
  }

  // "pacientes/historial-clinico": admin sees every doctor's encounters for
  // this patient, a doctor sees only their OWN encounters with this patient
  // (see ClinicalAccessGuard for why a non-treating doctor gets nothing
  // rather than a redacted row).
  public Page<EncounterDTO> getHistoryForPatient(UUID patientUuid, Authentication auth, Pageable pageable) {
    UUID doctorFilter = clinicalAccessGuard.resolveDoctorFilter(auth);

    Page<Encounter> page = doctorFilter == null
        ? encounterRepository.findByPatientUuid(patientUuid, pageable)
        : encounterRepository.findByPatientUuidAndDoctorUuid(patientUuid, doctorFilter, pageable);

    clinicalAccessLogService.record(patientUuid, auth, ClinicalResourceType.ENCOUNTER_LIST, null);
    return page.map(this::mapToDTO);
  }

  // GET /api/encounters/me — Flutter's own clinical history. Mirrors
  // TurnController#getMyTurns: the caller's uuid IS the patient filter, no
  // separate authorization check needed (a doctor/operator's uuid will
  // simply never match a Patient row).
  public Page<EncounterDTO> getMyHistory(UUID patientUuid, Authentication auth, Pageable pageable) {
    Page<Encounter> page = encounterRepository.findByPatientUuid(patientUuid, pageable);
    clinicalAccessLogService.record(patientUuid, auth, ClinicalResourceType.ENCOUNTER_LIST, null);
    return page.map(this::mapToDTO);
  }

  // Corrections only (reasonForVisit/clinicalNotes/diagnosis) — the turn this
  // encounter belongs to never changes. No delete: see apply report for why
  // Encounter/Prescription are never hard-deletable through this API.
  @Transactional
  public EncounterDTO update(Long id, EncounterDTO dto, Authentication auth) {
    Encounter encounter = findByIdOrThrow(id);
    clinicalAccessGuard.assertCanAccessEncounter(auth, resolveTreatingDoctorUuid(encounter));

    encounter.setReasonForVisit(dto.getReasonForVisit());
    encounter.setClinicalNotes(dto.getClinicalNotes());
    encounter.setDiagnosis(dto.getDiagnosis());

    Encounter updated = encounterRepository.save(encounter);
    return mapToDTO(updated);
  }

  private Encounter findByIdOrThrow(Long id) {
    return encounterRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Historia clínica no encontrada"));
  }

  private UUID resolveTreatingDoctorUuid(Encounter encounter) {
    Doctor doctor = doctorOf(encounter);
    return doctor != null ? doctor.getUuid() : null;
  }

  private UUID resolvePatientUuid(Encounter encounter) {
    if (encounter.getTurn() == null || encounter.getTurn().getPatient() == null) {
      return null;
    }
    return encounter.getTurn().getPatient().getUuid();
  }

  private Doctor doctorOf(Encounter encounter) {
    Turn turn = encounter.getTurn();
    if (turn == null || turn.getSchedule() == null) {
      return null;
    }
    return turn.getSchedule().getDoctor();
  }

  private EncounterDTO mapToDTO(Encounter entity) {
    Doctor doctor = doctorOf(entity);
    Schedule schedule = entity.getTurn() != null ? entity.getTurn().getSchedule() : null;

    return EncounterDTO.builder()
        .id(entity.getId())
        .turnId(entity.getTurn() != null ? entity.getTurn().getId() : null)
        .reasonForVisit(entity.getReasonForVisit())
        .clinicalNotes(entity.getClinicalNotes())
        .diagnosis(entity.getDiagnosis())
        .createdAt(entity.getCreatedAt())
        .doctorUuid(doctor != null ? doctor.getUuid() : null)
        .doctorFullName(doctor != null ? (doctor.getFirstName() + " " + doctor.getLastName()) : null)
        .visitDate(schedule != null ? schedule.getDate() : (LocalDate) null)
        .build();
  }
}
