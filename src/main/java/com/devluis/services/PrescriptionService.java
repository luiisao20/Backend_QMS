package com.devluis.services;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devluis.dto.PrescriptionDTO;
import com.devluis.dto.PrescriptionItemDTO;
import com.devluis.entity.Doctor;
import com.devluis.entity.Encounter;
import com.devluis.entity.Prescription;
import com.devluis.entity.PrescriptionItem;
import com.devluis.repository.EncounterRepository;
import com.devluis.repository.PrescriptionRepository;
import com.devluis.types.ClinicalResourceType;

import lombok.Data;

// No update()/delete(): a Prescription is treated as immutable once issued.
// See Prescription entity for the full justification. A correction is a NEW
// prescription (a new create() call), never an edit of the old one.
@Service
@Data
public class PrescriptionService {
  private final PrescriptionRepository prescriptionRepository;
  private final EncounterRepository encounterRepository;
  private final ClinicalAccessGuard clinicalAccessGuard;
  private final ClinicalAccessLogService clinicalAccessLogService;

  @Transactional
  public PrescriptionDTO create(PrescriptionDTO dto, Authentication auth) {
    Encounter encounter = encounterRepository.findById(dto.getEncounterId())
        .orElseThrow(() -> new RuntimeException("Historia clínica no encontrada"));

    if (dto.getItems() == null || dto.getItems().isEmpty()) {
      throw new RuntimeException("La receta debe tener al menos un medicamento");
    }

    clinicalAccessGuard.assertCanAccessEncounter(auth, resolveTreatingDoctorUuid(encounter));

    Prescription prescription = Prescription.builder()
        .encounter(encounter)
        .notes(dto.getNotes())
        .build();

    List<PrescriptionItem> items = dto.getItems().stream()
        .map(itemDto -> PrescriptionItem.builder()
            .prescription(prescription)
            .medication(itemDto.getMedication())
            .dosage(itemDto.getDosage())
            .frequency(itemDto.getFrequency())
            .duration(itemDto.getDuration())
            .instructions(itemDto.getInstructions())
            .build())
        .collect(Collectors.toList());
    prescription.setItems(items);

    Prescription saved = prescriptionRepository.save(prescription);
    return mapToDTO(saved);
  }

  public PrescriptionDTO getById(Long id, Authentication auth) {
    Prescription prescription = findByIdOrThrow(id);
    clinicalAccessGuard.assertCanAccessEncounter(auth, resolveTreatingDoctorUuid(prescription.getEncounter()));

    clinicalAccessLogService.record(
        resolvePatientUuid(prescription), auth, ClinicalResourceType.PRESCRIPTION, prescription.getId());

    return mapToDTO(prescription);
  }

  // "pacientes/recetas": admin sees every doctor's prescriptions for this
  // patient, a doctor sees only prescriptions from encounters THEY treated.
  public Page<PrescriptionDTO> getHistoryForPatient(UUID patientUuid, Authentication auth, Pageable pageable) {
    UUID doctorFilter = clinicalAccessGuard.resolveDoctorFilter(auth);

    Page<Prescription> page = doctorFilter == null
        ? prescriptionRepository.findByPatientUuid(patientUuid, pageable)
        : prescriptionRepository.findByPatientUuidAndDoctorUuid(patientUuid, doctorFilter, pageable);

    clinicalAccessLogService.record(patientUuid, auth, ClinicalResourceType.PRESCRIPTION_LIST, null);
    return page.map(this::mapToDTO);
  }

  // GET /api/prescriptions/me — Flutter's own prescriptions.
  public Page<PrescriptionDTO> getMyPrescriptions(UUID patientUuid, Authentication auth, Pageable pageable) {
    Page<Prescription> page = prescriptionRepository.findByPatientUuid(patientUuid, pageable);
    clinicalAccessLogService.record(patientUuid, auth, ClinicalResourceType.PRESCRIPTION_LIST, null);
    return page.map(this::mapToDTO);
  }

  private Prescription findByIdOrThrow(Long id) {
    return prescriptionRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Receta no encontrada"));
  }

  private UUID resolveTreatingDoctorUuid(Encounter encounter) {
    Doctor doctor = doctorOf(encounter);
    return doctor != null ? doctor.getUuid() : null;
  }

  private UUID resolvePatientUuid(Prescription prescription) {
    Encounter encounter = prescription.getEncounter();
    if (encounter == null || encounter.getTurn() == null || encounter.getTurn().getPatient() == null) {
      return null;
    }
    return encounter.getTurn().getPatient().getUuid();
  }

  private Doctor doctorOf(Encounter encounter) {
    if (encounter == null || encounter.getTurn() == null || encounter.getTurn().getSchedule() == null) {
      return null;
    }
    return encounter.getTurn().getSchedule().getDoctor();
  }

  private PrescriptionDTO mapToDTO(Prescription entity) {
    Doctor doctor = doctorOf(entity.getEncounter());

    List<PrescriptionItemDTO> items = entity.getItems() == null
        ? List.of()
        : entity.getItems().stream().map(this::mapItemToDTO).collect(Collectors.toList());

    return PrescriptionDTO.builder()
        .id(entity.getId())
        .encounterId(entity.getEncounter() != null ? entity.getEncounter().getId() : null)
        .notes(entity.getNotes())
        .items(items)
        .createdAt(entity.getCreatedAt())
        .doctorUuid(doctor != null ? doctor.getUuid() : null)
        .doctorFullName(doctor != null ? (doctor.getFirstName() + " " + doctor.getLastName()) : null)
        .build();
  }

  private PrescriptionItemDTO mapItemToDTO(PrescriptionItem item) {
    return PrescriptionItemDTO.builder()
        .id(item.getId())
        .medication(item.getMedication())
        .dosage(item.getDosage())
        .frequency(item.getFrequency())
        .duration(item.getDuration())
        .instructions(item.getInstructions())
        .build();
  }
}
