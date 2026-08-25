package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.devluis.dto.PrescriptionDTO;
import com.devluis.dto.PrescriptionItemDTO;
import com.devluis.entity.Doctor;
import com.devluis.entity.Encounter;
import com.devluis.entity.Patient;
import com.devluis.entity.Prescription;
import com.devluis.entity.Schedule;
import com.devluis.entity.Turn;
import com.devluis.repository.EncounterRepository;
import com.devluis.repository.PrescriptionRepository;
import com.devluis.types.ClinicalResourceType;
import com.devluis.types.TurnStatus;

@ExtendWith(MockitoExtension.class)
class PrescriptionServiceTest {

  @Mock
  private PrescriptionRepository prescriptionRepository;
  @Mock
  private EncounterRepository encounterRepository;
  @Mock
  private ClinicalAccessLogService clinicalAccessLogService;

  private PrescriptionService prescriptionService;

  private final UUID doctorUuid = UUID.randomUUID();
  private final UUID patientUuid = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    prescriptionService = new PrescriptionService(
        prescriptionRepository, encounterRepository, new ClinicalAccessGuard(), clinicalAccessLogService);
  }

  private Authentication authOf(UUID uuid, String role) {
    return new UsernamePasswordAuthenticationToken(uuid.toString(), null, List.of(new SimpleGrantedAuthority(role)));
  }

  private Encounter encounterOf(long id) {
    Doctor doctor = Doctor.builder().uuid(doctorUuid).firstName("Ana").lastName("Diaz").build();
    Schedule schedule = Schedule.builder().id(1L).doctor(doctor).build();
    Patient patient = Patient.builder().uuid(patientUuid).firstName("Luis").lastName("Perez").build();
    Turn turn = Turn.builder().id(id + 1000).status(TurnStatus.TURN_TREATED).schedule(schedule).patient(patient).build();
    return Encounter.builder().id(id).turn(turn).reasonForVisit("R").diagnosis("D").build();
  }

  private PrescriptionItemDTO item(String medication, String dosage, String frequency, String duration) {
    return PrescriptionItemDTO.builder()
        .medication(medication).dosage(dosage).frequency(frequency).duration(duration).build();
  }

  // --- create: the required "prescription with several items" scenario ----

  @Test
  void create_savesPrescriptionWithSeveralItems_eachKeepingItsOwnDosageFrequencyAndDuration() {
    Encounter encounter = encounterOf(1L);
    when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
    when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(inv -> {
      Prescription p = inv.getArgument(0);
      p.setId(500L);
      return p;
    });

    PrescriptionDTO dto = PrescriptionDTO.builder()
        .encounterId(1L)
        .notes("Tomar con abundante agua")
        .items(List.of(
            item("Amoxicilina", "500mg", "cada 8 horas", "por 7 días"),
            item("Ibuprofeno", "400mg", "cada 12 horas", "por 3 días"),
            item("Paracetamol", "500mg", "cada 6 horas", "por 3 días")))
        .build();

    PrescriptionDTO result = prescriptionService.create(dto, authOf(doctorUuid, "ROLE_DOCTOR"));

    assertThat(result.getId()).isEqualTo(500L);
    assertThat(result.getItems()).hasSize(3);
    assertThat(result.getItems().get(0).getMedication()).isEqualTo("Amoxicilina");
    assertThat(result.getItems().get(0).getDosage()).isEqualTo("500mg");
    assertThat(result.getItems().get(1).getFrequency()).isEqualTo("cada 12 horas");
    assertThat(result.getItems().get(2).getDuration()).isEqualTo("por 3 días");
    assertThat(result.getDoctorUuid()).isEqualTo(doctorUuid);
  }

  @Test
  void create_throws_whenItemsListIsEmpty() {
    Encounter encounter = encounterOf(2L);
    when(encounterRepository.findById(2L)).thenReturn(Optional.of(encounter));

    PrescriptionDTO dto = PrescriptionDTO.builder().encounterId(2L).items(List.of()).build();

    assertThatThrownBy(() -> prescriptionService.create(dto, authOf(doctorUuid, "ROLE_DOCTOR")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("medicamento");

    verify(prescriptionRepository, never()).save(any());
  }

  @Test
  void create_throws_whenEncounterNotFound() {
    when(encounterRepository.findById(999L)).thenReturn(Optional.empty());

    PrescriptionDTO dto = PrescriptionDTO.builder().encounterId(999L).items(List.of(item("X", "1", "1", "1"))).build();

    assertThatThrownBy(() -> prescriptionService.create(dto, authOf(doctorUuid, "ROLE_DOCTOR")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");
  }

  @Test
  void create_throws_whenCallerIsADifferentDoctorThanTheEncountersTreatingDoctor() {
    Encounter encounter = encounterOf(3L);
    when(encounterRepository.findById(3L)).thenReturn(Optional.of(encounter));

    PrescriptionDTO dto = PrescriptionDTO.builder().encounterId(3L).items(List.of(item("X", "1", "1", "1"))).build();

    assertThatThrownBy(() -> prescriptionService.create(dto, authOf(UUID.randomUUID(), "ROLE_DOCTOR")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("permisos");

    verify(prescriptionRepository, never()).save(any());
  }

  @Test
  void create_savesPrescription_whenCallerIsAdmin() {
    Encounter encounter = encounterOf(4L);
    when(encounterRepository.findById(4L)).thenReturn(Optional.of(encounter));
    when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(inv -> inv.getArgument(0));

    PrescriptionDTO dto = PrescriptionDTO.builder().encounterId(4L).items(List.of(item("X", "1", "1", "1"))).build();

    PrescriptionDTO result = prescriptionService.create(dto, authOf(UUID.randomUUID(), "ROLE_ADMIN"));

    assertThat(result.getItems()).hasSize(1);
  }

  // --- getById --------------------------------------------------------------

  @Test
  void getById_returnsPrescription_whenCallerIsTheTreatingDoctor_andRecordsTheAccess() {
    Encounter encounter = encounterOf(5L);
    Prescription prescription = Prescription.builder().id(50L).encounter(encounter).items(List.of()).build();
    when(prescriptionRepository.findById(50L)).thenReturn(Optional.of(prescription));
    Authentication auth = authOf(doctorUuid, "ROLE_DOCTOR");

    PrescriptionDTO result = prescriptionService.getById(50L, auth);

    assertThat(result.getId()).isEqualTo(50L);
    verify(clinicalAccessLogService).record(patientUuid, auth, ClinicalResourceType.PRESCRIPTION, 50L);
  }

  @Test
  void getById_throws_whenCallerIsADifferentDoctor_andNeverRecordsTheDeniedAttempt() {
    Encounter encounter = encounterOf(6L);
    Prescription prescription = Prescription.builder().id(60L).encounter(encounter).items(List.of()).build();
    when(prescriptionRepository.findById(60L)).thenReturn(Optional.of(prescription));

    assertThatThrownBy(() -> prescriptionService.getById(60L, authOf(UUID.randomUUID(), "ROLE_DOCTOR")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("permisos");

    verify(clinicalAccessLogService, never()).record(any(), any(), any(), any());
  }

  @Test
  void getById_throws_whenNotFound() {
    when(prescriptionRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> prescriptionService.getById(404L, authOf(UUID.randomUUID(), "ROLE_ADMIN")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");
  }

  // --- getHistoryForPatient / getMyPrescriptions ----------------------------

  @Test
  void getHistoryForPatient_filtersToOnlyTheCallersOwnPrescriptions_whenCallerIsDoctor() {
    Pageable pageable = PageRequest.of(0, 10);
    when(prescriptionRepository.findByPatientUuidAndDoctorUuid(eq(patientUuid), eq(doctorUuid), any(Pageable.class)))
        .thenReturn(Page.empty());
    Authentication auth = authOf(doctorUuid, "ROLE_DOCTOR");

    prescriptionService.getHistoryForPatient(patientUuid, auth, pageable);

    verify(prescriptionRepository).findByPatientUuidAndDoctorUuid(eq(patientUuid), eq(doctorUuid), any(Pageable.class));
    verify(prescriptionRepository, never()).findByPatientUuid(any(), any());
    verify(clinicalAccessLogService).record(patientUuid, auth, ClinicalResourceType.PRESCRIPTION_LIST, null);
  }

  @Test
  void getHistoryForPatient_returnsEveryDoctorsPrescriptions_whenCallerIsAdmin() {
    Pageable pageable = PageRequest.of(0, 10);
    Encounter encounter = encounterOf(7L);
    Prescription prescription = Prescription.builder().id(70L).encounter(encounter).items(List.of()).build();
    when(prescriptionRepository.findByPatientUuid(eq(patientUuid), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(prescription)));

    Page<PrescriptionDTO> result = prescriptionService.getHistoryForPatient(
        patientUuid, authOf(UUID.randomUUID(), "ROLE_ADMIN"), pageable);

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  void getHistoryForPatient_throws_whenCallerIsEmployee() {
    assertThatThrownBy(() -> prescriptionService.getHistoryForPatient(
        patientUuid, authOf(UUID.randomUUID(), "ROLE_EMPLOYEE"), PageRequest.of(0, 10)))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void getMyPrescriptions_returnsThePatientsOwn_andRecordsTheAccess() {
    Pageable pageable = PageRequest.of(0, 10);
    when(prescriptionRepository.findByPatientUuid(eq(patientUuid), any(Pageable.class))).thenReturn(Page.empty());
    Authentication auth = authOf(patientUuid, "ROLE_PATIENT");

    prescriptionService.getMyPrescriptions(patientUuid, auth, pageable);

    verify(clinicalAccessLogService).record(patientUuid, auth, ClinicalResourceType.PRESCRIPTION_LIST, null);
  }
}
