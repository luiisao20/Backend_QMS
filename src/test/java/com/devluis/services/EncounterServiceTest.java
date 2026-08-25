package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.devluis.dto.EncounterDTO;
import com.devluis.entity.Doctor;
import com.devluis.entity.Encounter;
import com.devluis.entity.Patient;
import com.devluis.entity.Schedule;
import com.devluis.entity.Turn;
import com.devluis.repository.EncounterRepository;
import com.devluis.repository.TurnRepository;
import com.devluis.types.ClinicalResourceType;
import com.devluis.types.TurnStatus;

@ExtendWith(MockitoExtension.class)
class EncounterServiceTest {

  @Mock
  private EncounterRepository encounterRepository;
  @Mock
  private TurnRepository turnRepository;
  @Mock
  private ClinicalAccessLogService clinicalAccessLogService;

  private EncounterService encounterService;

  private final UUID doctorUuid = UUID.randomUUID();
  private final UUID patientUuid = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    // Real collaborator, not a mock: ClinicalAccessGuard has no dependencies
    // and its own dedicated test suite (ClinicalAccessGuardTest) already
    // covers its rules in isolation.
    encounterService = new EncounterService(
        encounterRepository, turnRepository, new ClinicalAccessGuard(), clinicalAccessLogService);
  }

  private Authentication authOf(UUID uuid, String role) {
    return new UsernamePasswordAuthenticationToken(uuid.toString(), null, List.of(new SimpleGrantedAuthority(role)));
  }

  private Turn treatedTurn(long id) {
    Doctor doctor = Doctor.builder().uuid(doctorUuid).firstName("Ana").lastName("Diaz").build();
    Schedule schedule = Schedule.builder().id(1L).doctor(doctor).build();
    Patient patient = Patient.builder().uuid(patientUuid).firstName("Luis").lastName("Perez").build();
    return Turn.builder().id(id).status(TurnStatus.TURN_TREATED).schedule(schedule).patient(patient).build();
  }

  private EncounterDTO validDto(Long turnId) {
    return EncounterDTO.builder()
        .turnId(turnId)
        .reasonForVisit("Dolor abdominal")
        .clinicalNotes("Paciente refiere dolor hace 2 días")
        .diagnosis("Gastritis")
        .build();
  }

  // --- create -----------------------------------------------------------

  @Test
  void create_savesEncounter_whenTurnIsTreatedAndCallerIsTheTreatingDoctor() {
    Turn turn = treatedTurn(10L);
    when(turnRepository.findById(10L)).thenReturn(Optional.of(turn));
    when(encounterRepository.existsByTurnId(10L)).thenReturn(false);
    when(encounterRepository.save(any(Encounter.class))).thenAnswer(inv -> {
      Encounter e = inv.getArgument(0);
      e.setId(100L);
      return e;
    });

    EncounterDTO result = encounterService.create(validDto(10L), authOf(doctorUuid, "ROLE_DOCTOR"));

    assertThat(result.getId()).isEqualTo(100L);
    assertThat(result.getDiagnosis()).isEqualTo("Gastritis");
    assertThat(result.getDoctorUuid()).isEqualTo(doctorUuid);
  }

  @Test
  void create_savesEncounter_whenCallerIsAdmin_evenThoughNotTheTreatingDoctor() {
    Turn turn = treatedTurn(11L);
    when(turnRepository.findById(11L)).thenReturn(Optional.of(turn));
    when(encounterRepository.existsByTurnId(11L)).thenReturn(false);
    when(encounterRepository.save(any(Encounter.class))).thenAnswer(inv -> inv.getArgument(0));

    EncounterDTO result = encounterService.create(validDto(11L), authOf(UUID.randomUUID(), "ROLE_ADMIN"));

    assertThat(result.getDiagnosis()).isEqualTo("Gastritis");
  }

  @Test
  void create_throws_whenTurnNotFound() {
    when(turnRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> encounterService.create(validDto(999L), authOf(doctorUuid, "ROLE_DOCTOR")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Turno no encontrado");

    verify(encounterRepository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(value = TurnStatus.class, names = {"TURN_TREATED"}, mode = EnumSource.Mode.EXCLUDE)
  void create_throws_whenTurnIsNotYetTreated_includingWhenItWasCancelled(TurnStatus notTreated) {
    Turn turn = treatedTurn(12L);
    turn.setStatus(notTreated);
    when(turnRepository.findById(12L)).thenReturn(Optional.of(turn));

    assertThatThrownBy(() -> encounterService.create(validDto(12L), authOf(doctorUuid, "ROLE_DOCTOR")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("atendido");

    verify(encounterRepository, never()).save(any());
  }

  @Test
  void create_throws_whenTurnHasNoDoctorAssigned() {
    Turn turn = Turn.builder().id(13L).status(TurnStatus.TURN_TREATED)
        .schedule(Schedule.builder().id(2L).build())
        .patient(Patient.builder().uuid(patientUuid).build())
        .build();
    when(turnRepository.findById(13L)).thenReturn(Optional.of(turn));

    assertThatThrownBy(() -> encounterService.create(validDto(13L), authOf(UUID.randomUUID(), "ROLE_ADMIN")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("doctor");

    verify(encounterRepository, never()).save(any());
  }

  @Test
  void create_throws_whenAnEncounterAlreadyExistsForThatTurn() {
    Turn turn = treatedTurn(14L);
    when(turnRepository.findById(14L)).thenReturn(Optional.of(turn));
    when(encounterRepository.existsByTurnId(14L)).thenReturn(true);

    assertThatThrownBy(() -> encounterService.create(validDto(14L), authOf(doctorUuid, "ROLE_DOCTOR")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Ya existe");

    verify(encounterRepository, never()).save(any());
  }

  @Test
  void create_throws_whenCallerIsADifferentDoctorThanTheOneOnTheTurn() {
    Turn turn = treatedTurn(15L);
    when(turnRepository.findById(15L)).thenReturn(Optional.of(turn));
    when(encounterRepository.existsByTurnId(15L)).thenReturn(false);
    UUID anotherDoctor = UUID.randomUUID();

    assertThatThrownBy(() -> encounterService.create(validDto(15L), authOf(anotherDoctor, "ROLE_DOCTOR")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("permisos");

    verify(encounterRepository, never()).save(any());
  }

  @Test
  void create_throws_whenCallerIsEmployee() {
    Turn turn = treatedTurn(16L);
    when(turnRepository.findById(16L)).thenReturn(Optional.of(turn));
    when(encounterRepository.existsByTurnId(16L)).thenReturn(false);

    assertThatThrownBy(() -> encounterService.create(validDto(16L), authOf(UUID.randomUUID(), "ROLE_EMPLOYEE")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("permisos");

    verify(encounterRepository, never()).save(any());
  }

  // --- getById ------------------------------------------------------------

  @Test
  void getById_returnsEncounter_whenCallerIsTheTreatingDoctor_andRecordsTheAccess() {
    Turn turn = treatedTurn(20L);
    Encounter encounter = Encounter.builder().id(200L).turn(turn).reasonForVisit("R").diagnosis("D").build();
    when(encounterRepository.findById(200L)).thenReturn(Optional.of(encounter));
    Authentication auth = authOf(doctorUuid, "ROLE_DOCTOR");

    EncounterDTO result = encounterService.getById(200L, auth);

    assertThat(result.getId()).isEqualTo(200L);
    verify(clinicalAccessLogService).record(patientUuid, auth, ClinicalResourceType.ENCOUNTER, 200L);
  }

  @Test
  void getById_returnsEncounter_whenCallerIsAdmin() {
    Turn turn = treatedTurn(21L);
    Encounter encounter = Encounter.builder().id(201L).turn(turn).reasonForVisit("R").diagnosis("D").build();
    when(encounterRepository.findById(201L)).thenReturn(Optional.of(encounter));

    EncounterDTO result = encounterService.getById(201L, authOf(UUID.randomUUID(), "ROLE_ADMIN"));

    assertThat(result.getId()).isEqualTo(201L);
  }

  @Test
  void getById_throws_whenCallerIsADifferentDoctor_andNeverRecordsTheDeniedAttempt() {
    Turn turn = treatedTurn(22L);
    Encounter encounter = Encounter.builder().id(202L).turn(turn).reasonForVisit("R").diagnosis("D").build();
    when(encounterRepository.findById(202L)).thenReturn(Optional.of(encounter));

    assertThatThrownBy(() -> encounterService.getById(202L, authOf(UUID.randomUUID(), "ROLE_DOCTOR")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("permisos");

    verify(clinicalAccessLogService, never()).record(any(), any(), any(), any());
  }

  @Test
  void getById_throws_whenNotFound() {
    when(encounterRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> encounterService.getById(404L, authOf(UUID.randomUUID(), "ROLE_ADMIN")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");
  }

  // --- getHistoryForPatient (staff "historial-clinico" screen) -------------

  @Test
  void getHistoryForPatient_returnsEveryDoctorsEncounters_whenCallerIsAdmin_andLogsOneListAccess() {
    Pageable pageable = PageRequest.of(0, 10);
    Turn turn = treatedTurn(30L);
    Encounter encounter = Encounter.builder().id(300L).turn(turn).reasonForVisit("R").diagnosis("D").build();
    when(encounterRepository.findByPatientUuid(eq(patientUuid), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(encounter)));
    Authentication auth = authOf(UUID.randomUUID(), "ROLE_ADMIN");

    Page<EncounterDTO> result = encounterService.getHistoryForPatient(patientUuid, auth, pageable);

    assertThat(result.getContent()).hasSize(1);
    verify(encounterRepository, never()).findByPatientUuidAndDoctorUuid(any(), any(), any());
    verify(clinicalAccessLogService).record(patientUuid, auth, ClinicalResourceType.ENCOUNTER_LIST, null);
  }

  @Test
  void getHistoryForPatient_filtersToOnlyTheCallersOwnEncounters_whenCallerIsDoctor() {
    Pageable pageable = PageRequest.of(0, 10);
    when(encounterRepository.findByPatientUuidAndDoctorUuid(eq(patientUuid), eq(doctorUuid), any(Pageable.class)))
        .thenReturn(Page.empty());
    Authentication auth = authOf(doctorUuid, "ROLE_DOCTOR");

    encounterService.getHistoryForPatient(patientUuid, auth, pageable);

    verify(encounterRepository).findByPatientUuidAndDoctorUuid(eq(patientUuid), eq(doctorUuid), any(Pageable.class));
    verify(encounterRepository, never()).findByPatientUuid(any(), any());
  }

  @Test
  void getHistoryForPatient_throws_whenCallerIsEmployee() {
    assertThatThrownBy(() -> encounterService.getHistoryForPatient(
        patientUuid, authOf(UUID.randomUUID(), "ROLE_EMPLOYEE"), PageRequest.of(0, 10)))
        .isInstanceOf(RuntimeException.class);

    verify(clinicalAccessLogService, never()).record(any(), any(), any(), any());
  }

  // --- getMyHistory (Flutter "/me") ----------------------------------------

  @Test
  void getMyHistory_returnsThePatientsOwnEncounters_andRecordsTheAccess() {
    Pageable pageable = PageRequest.of(0, 10);
    when(encounterRepository.findByPatientUuid(eq(patientUuid), any(Pageable.class))).thenReturn(Page.empty());
    Authentication auth = authOf(patientUuid, "ROLE_PATIENT");

    encounterService.getMyHistory(patientUuid, auth, pageable);

    verify(clinicalAccessLogService).record(patientUuid, auth, ClinicalResourceType.ENCOUNTER_LIST, null);
  }

  // --- update ---------------------------------------------------------------

  @Test
  void update_changesNotesAndDiagnosis_whenCallerIsTheTreatingDoctor() {
    Turn turn = treatedTurn(40L);
    Encounter encounter = Encounter.builder().id(400L).turn(turn).reasonForVisit("Old").diagnosis("Old dx").build();
    when(encounterRepository.findById(400L)).thenReturn(Optional.of(encounter));
    when(encounterRepository.save(any(Encounter.class))).thenAnswer(inv -> inv.getArgument(0));

    EncounterDTO dto = EncounterDTO.builder()
        .reasonForVisit("Actualizado").clinicalNotes("Notas nuevas").diagnosis("Nuevo dx").build();

    EncounterDTO result = encounterService.update(400L, dto, authOf(doctorUuid, "ROLE_DOCTOR"));

    assertThat(result.getDiagnosis()).isEqualTo("Nuevo dx");
    assertThat(result.getReasonForVisit()).isEqualTo("Actualizado");
  }

  @Test
  void update_throws_whenCallerIsADifferentDoctor() {
    Turn turn = treatedTurn(41L);
    Encounter encounter = Encounter.builder().id(401L).turn(turn).reasonForVisit("Old").diagnosis("Old dx").build();
    when(encounterRepository.findById(401L)).thenReturn(Optional.of(encounter));

    EncounterDTO dto = EncounterDTO.builder().reasonForVisit("x").diagnosis("y").build();

    assertThatThrownBy(() -> encounterService.update(401L, dto, authOf(UUID.randomUUID(), "ROLE_DOCTOR")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("permisos");

    verify(encounterRepository, times(1)).findById(401L);
    verify(encounterRepository, never()).save(any());
  }
}
