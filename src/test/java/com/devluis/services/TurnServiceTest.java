package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.devluis.dto.PatientDTO;
import com.devluis.dto.ScheduleDTO;
import com.devluis.dto.TurnBoardDTO;
import com.devluis.dto.TurnDTO;
import com.devluis.entity.Doctor;
import com.devluis.entity.Patient;
import com.devluis.entity.Schedule;
import com.devluis.entity.Servicio;
import com.devluis.entity.Stablishment;
import com.devluis.entity.Turn;
import com.devluis.repository.OperatorRepository;
import com.devluis.repository.PatientRepository;
import com.devluis.repository.ScheduleRepository;
import com.devluis.repository.TurnRepository;
import com.devluis.types.ScheduleStatus;
import com.devluis.types.TurnStatus;

@ExtendWith(MockitoExtension.class)
class TurnServiceTest {

  @Mock
  private TurnRepository turnRepository;
  @Mock
  private PatientRepository patientRepository;
  @Mock
  private ScheduleRepository scheduleRepository;
  @Mock
  private OperatorRepository operatorRepository;
  @Mock
  private SimpMessagingTemplate messagingTemplate;
  @Mock
  private MailService mailService;

  @Mock
  private com.devluis.repository.ConsultorioRepository consultorioRepository;

  private TurnService turnService;

  @BeforeEach
  void setUp() {
    turnService = new TurnService(
        turnRepository, patientRepository, scheduleRepository, operatorRepository, messagingTemplate, mailService,
        consultorioRepository);
  }

  // -- fixtures --------------------------------------------------------------

  private Doctor buildDoctor(UUID uuid) {
    return Doctor.builder()
        .uuid(uuid)
        .firstName("Carla")
        .lastName("Mendez")
        .speciality("Cardiologia")
        .build();
  }

  private Servicio buildService(Long id) {
    return Servicio.builder()
        .id(id)
        .name("Cardiologia")
        .price(10f)
        .build();
  }

  private Stablishment buildStablishment(Long id) {
    return Stablishment.builder()
        .id(id)
        .name("Sede Norte")
        .address("Av. Siempre Viva 123")
        .build();
  }

  /**
   * A slot a WEEK from today, never a fixed calendar date.
   *
   * create() refuses a slot whose start time has already passed, so every
   * booking fixture has to be in the future to exercise anything past that
   * guard. A hardcoded date satisfies that only until the date arrives: this
   * fixture used to read LocalDate.of(2026, 9, 1), which would have turned
   * every create() test in this file red on that morning, for a reason none
   * of them are about.
   */
  private Schedule buildSchedule(Long id, Doctor doctor, Servicio service, Stablishment stablishment, ScheduleStatus status) {
    return buildScheduleAt(id, LocalDate.now().plusDays(7), LocalTime.of(9, 0), doctor, service, stablishment, status);
  }

  private Schedule buildScheduleAt(Long id, LocalDate date, LocalTime hour, Doctor doctor, Servicio service, Stablishment stablishment, ScheduleStatus status) {
    return Schedule.builder()
        .id(id)
        .date(date)
        .hour(hour)
        .status(status)
        .doctor(doctor)
        .service(service)
        .stablishment(stablishment)
        .build();
  }

  private Patient buildPatient(UUID uuid) {
    return Patient.builder()
        .uuid(uuid)
        .email("paciente@example.com")
        .firstName("Ana")
        .lastName("Torres")
        .ci("1234567890")
        .phone("0999999999")
        .build();
  }

  private Turn buildTurn(Long id, TurnStatus status, Patient patient, Schedule schedule) {
    return Turn.builder()
        .id(id)
        .order(1)
        .status(status)
        .patient(patient)
        .schedule(schedule)
        .build();
  }

  // -- create: a slot that has already started is not bookable ---------------
  //
  // The clock is a PARAMETER of requireUpcoming rather than a LocalDateTime.now()
  // read inside it, which is what lets these assert the minute-level boundary
  // instead of approximating it with "yesterday" and "tomorrow". The two tests
  // that DO go through create() cover the wiring; these cover the rule.

  @Test
  void requireUpcoming_rejects_whenTheHourAlreadyPassedToday() {
    LocalDateTime now = LocalDateTime.of(2026, 8, 26, 11, 0);
    Schedule schedule = buildScheduleAt(1L, LocalDate.of(2026, 8, 26), LocalTime.of(8, 0),
        buildDoctor(UUID.randomUUID()), buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_FREE);

    assertThatThrownBy(() -> TurnService.requireUpcoming(schedule, now))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("ya paso");
  }

  @Test
  void requireUpcoming_rejects_whenTheSlotStartsExactlyNow() {
    LocalDateTime now = LocalDateTime.of(2026, 8, 26, 11, 0);
    Schedule schedule = buildScheduleAt(1L, LocalDate.of(2026, 8, 26), LocalTime.of(11, 0),
        buildDoctor(UUID.randomUUID()), buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_FREE);

    assertThatThrownBy(() -> TurnService.requireUpcoming(schedule, now))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void requireUpcoming_accepts_whenTheHourIsStillAheadToday() {
    LocalDateTime now = LocalDateTime.of(2026, 8, 26, 11, 0);
    Schedule schedule = buildScheduleAt(1L, LocalDate.of(2026, 8, 26), LocalTime.of(11, 20),
        buildDoctor(UUID.randomUUID()), buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_FREE);

    TurnService.requireUpcoming(schedule, now);
  }

  /**
   * The test that catches the naive implementation: comparing only the HOUR
   * would accept this slot, because 09:00 is after the 08:00 it is "now" — on
   * a day that ended yesterday.
   */
  @Test
  void requireUpcoming_rejects_yesterdaysSlot_evenWhenItsHourIsAheadOfTheClock() {
    LocalDateTime now = LocalDateTime.of(2026, 8, 26, 8, 0);
    Schedule schedule = buildScheduleAt(1L, LocalDate.of(2026, 8, 25), LocalTime.of(9, 0),
        buildDoctor(UUID.randomUUID()), buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_FREE);

    assertThatThrownBy(() -> TurnService.requireUpcoming(schedule, now))
        .isInstanceOf(RuntimeException.class);
  }

  /** The mirror of the above: tomorrow is bookable at an hour already gone today. */
  @Test
  void requireUpcoming_accepts_tomorrowsSlot_atAnHourAlreadyPassedToday() {
    LocalDateTime now = LocalDateTime.of(2026, 8, 26, 20, 0);
    Schedule schedule = buildScheduleAt(1L, LocalDate.of(2026, 8, 27), LocalTime.of(9, 0),
        buildDoctor(UUID.randomUUID()), buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_FREE);

    TurnService.requireUpcoming(schedule, now);
  }

  @Test
  void create_refusesToBook_andLeavesTheSlotUntouched_whenTheSlotAlreadyPassed() {
    UUID patientUuid = UUID.randomUUID();
    Schedule schedule = buildScheduleAt(10L, LocalDate.now().minusDays(1), LocalTime.of(9, 0),
        buildDoctor(UUID.randomUUID()), buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_FREE);
    TurnDTO dto = TurnDTO.builder().schedule(ScheduleDTO.builder().id(10L).build()).build();

    when(patientRepository.findById(patientUuid)).thenReturn(Optional.of(buildPatient(patientUuid)));
    when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));

    assertThatThrownBy(() -> turnService.create(dto, patientUuid.toString()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("ya paso");

    // A rejected booking must not half-happen: no turn row, and the slot stays
    // FREE so the clinic can still see it as an unused cupo rather than one
    // occupied by a turn that was never created.
    verify(turnRepository, never()).save(any());
    verify(scheduleRepository, never()).saveAndFlush(any());
    assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.STATUS_FREE);
  }

  // -- create: schedule occupancy + optimistic-lock concurrency guard --------

  @Test
  void create_occupiesTheSchedule_whenBookingSucceeds() {
    UUID patientUuid = UUID.randomUUID();
    Schedule schedule = buildSchedule(10L, buildDoctor(UUID.randomUUID()), buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_FREE);
    Patient patient = buildPatient(patientUuid);
    TurnDTO dto = TurnDTO.builder().schedule(ScheduleDTO.builder().id(10L).build()).build();

    when(patientRepository.findById(patientUuid)).thenReturn(Optional.of(patient));
    when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));
    when(scheduleRepository.saveAndFlush(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(turnRepository.countTurnsByServiceAndDate(1L, schedule.getDate())).thenReturn(0L);
    when(turnRepository.save(any(Turn.class))).thenAnswer(invocation -> invocation.getArgument(0));

    turnService.create(dto, patientUuid.toString());

    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).saveAndFlush(scheduleCaptor.capture());
    assertThat(scheduleCaptor.getValue().getStatus()).isEqualTo(ScheduleStatus.STATUS_OCCUPIED);
  }

  @Test
  void create_rejectsWithSpanishMessage_whenAnotherBookingWonTheRace() {
    UUID patientUuid = UUID.randomUUID();
    Schedule schedule = buildSchedule(10L, buildDoctor(UUID.randomUUID()), buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_FREE);
    Patient patient = buildPatient(patientUuid);
    TurnDTO dto = TurnDTO.builder().schedule(ScheduleDTO.builder().id(10L).build()).build();

    when(patientRepository.findById(patientUuid)).thenReturn(Optional.of(patient));
    when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));
    // Both requests read STATUS_FREE; this one loses the race at save-time.
    when(scheduleRepository.saveAndFlush(any(Schedule.class)))
        .thenThrow(new ObjectOptimisticLockingFailureException(Schedule.class, 10L));

    assertThatThrownBy(() -> turnService.create(dto, patientUuid.toString()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("reservado");

    // The loser must never create an orphan turn for a slot it didn't actually win.
    verify(turnRepository, never()).save(any());
  }

  @Test
  void createByStaff_rejectsWithSpanishMessage_whenAnotherBookingWonTheRace() {
    Patient patient = buildPatient(UUID.randomUUID());
    Schedule schedule = buildSchedule(10L, buildDoctor(UUID.randomUUID()), buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_FREE);
    TurnDTO dto = TurnDTO.builder()
        .patient(PatientDTO.builder().uuid(patient.getUuid()).build())
        .schedule(ScheduleDTO.builder().id(10L).build())
        .build();

    when(patientRepository.findById(patient.getUuid())).thenReturn(Optional.of(patient));
    when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));
    when(scheduleRepository.saveAndFlush(any(Schedule.class)))
        .thenThrow(new ObjectOptimisticLockingFailureException(Schedule.class, 10L));

    assertThatThrownBy(() -> turnService.createByStaff(dto, UUID.randomUUID().toString()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("reservado");

    verify(turnRepository, never()).save(any());
  }

  // -- broadcastTurnUpdate: anonymous board channel vs. per-user detail -------

  @Test
  void create_broadcastsAnonymousBoardPayload_withoutAnyPatientIdentifyingField() {
    UUID patientUuid = UUID.randomUUID();
    Doctor doctor = buildDoctor(UUID.randomUUID());
    Servicio service = buildService(1L);
    Stablishment stablishment = buildStablishment(1L);
    Schedule schedule = buildSchedule(10L, doctor, service, stablishment, ScheduleStatus.STATUS_FREE);
    Patient patient = buildPatient(patientUuid);
    TurnDTO dto = TurnDTO.builder().schedule(ScheduleDTO.builder().id(10L).build()).build();

    when(patientRepository.findById(patientUuid)).thenReturn(Optional.of(patient));
    when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));
    when(scheduleRepository.saveAndFlush(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(turnRepository.countTurnsByServiceAndDate(1L, schedule.getDate())).thenReturn(0L);
    when(turnRepository.save(any(Turn.class))).thenAnswer(invocation -> invocation.getArgument(0));

    turnService.create(dto, patientUuid.toString());

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(messagingTemplate).convertAndSend(
        eq("/topic/stablishment/1/" + schedule.getDate()), payloadCaptor.capture());

    // TurnBoardDTO has NO uuid/firstName/lastName/ci/email/phone accessor at
    // all — that is a compile-time guarantee, not a runtime null-check. This
    // assertion on its actual (non-identifying) fields is what keeps the
    // patient-data leak on this broadcast channel from silently coming back.
    assertThat(payloadCaptor.getValue()).isInstanceOf(TurnBoardDTO.class);
    TurnBoardDTO board = (TurnBoardDTO) payloadCaptor.getValue();
    assertThat(board.getOrder()).isEqualTo(1);
    assertThat(board.getStatus()).isEqualTo(TurnStatus.TURN_PENDING);
    assertThat(board.getServiceName()).isEqualTo("Cardiologia");
    assertThat(board.getDoctorName()).isEqualTo("Carla Mendez");
    assertThat(board.getStablishmentName()).isEqualTo("Sede Norte");
  }

  @Test
  void create_sendsFullDetailToThePatientsOwnChannel() {
    UUID patientUuid = UUID.randomUUID();
    Schedule schedule = buildSchedule(10L, buildDoctor(UUID.randomUUID()), buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_FREE);
    Patient patient = buildPatient(patientUuid);
    TurnDTO dto = TurnDTO.builder().schedule(ScheduleDTO.builder().id(10L).build()).build();

    when(patientRepository.findById(patientUuid)).thenReturn(Optional.of(patient));
    when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));
    when(scheduleRepository.saveAndFlush(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(turnRepository.countTurnsByServiceAndDate(1L, schedule.getDate())).thenReturn(0L);
    when(turnRepository.save(any(Turn.class))).thenAnswer(invocation -> invocation.getArgument(0));

    turnService.create(dto, patientUuid.toString());

    ArgumentCaptor<TurnDTO> dtoCaptor = ArgumentCaptor.forClass(TurnDTO.class);
    // Same mechanism already proven by the doctor channel below: convertAndSendToUser,
    // client subscribes to /user/topic/turns.
    verify(messagingTemplate).convertAndSendToUser(eq(patientUuid.toString()), eq("/topic/turns"), dtoCaptor.capture());
    assertThat(dtoCaptor.getValue().getPatient().getUuid()).isEqualTo(patientUuid);
  }

  // -- cancelTurn: schedule release, guarded by other active turns / STATUS_UNAVAILABLE --

  @Test
  void cancelTurn_releasesTheSchedule_whenNoOtherActiveTurnHoldsIt() {
    UUID patientUuid = UUID.randomUUID();
    Schedule schedule = buildSchedule(1L, buildDoctor(UUID.randomUUID()), buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_OCCUPIED);
    Patient patient = buildPatient(patientUuid);
    Turn turn = buildTurn(5L, TurnStatus.TURN_PENDING, patient, schedule);

    when(turnRepository.findById(5L)).thenReturn(Optional.of(turn));
    when(turnRepository.save(any(Turn.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(turnRepository.existsByScheduleIdAndStatusNotAndIdNot(1L, TurnStatus.TURN_CANCELLED, 5L)).thenReturn(false);
    when(scheduleRepository.saveAndFlush(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

    turnService.cancelTurn(5L, patientUuid.toString());

    ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
    verify(scheduleRepository).saveAndFlush(scheduleCaptor.capture());
    assertThat(scheduleCaptor.getValue().getStatus()).isEqualTo(ScheduleStatus.STATUS_FREE);
  }

  @Test
  void cancelTurn_doesNotReleaseTheSchedule_whenAnotherActiveTurnStillHoldsIt() {
    // Simulates data left over from before this fix: two non-cancelled turns
    // already pointing at the same schedule. Cancelling one must NOT free a
    // slot the other still legitimately (if inconsistently) occupies.
    UUID patientUuid = UUID.randomUUID();
    Schedule schedule = buildSchedule(1L, buildDoctor(UUID.randomUUID()), buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_OCCUPIED);
    Patient patient = buildPatient(patientUuid);
    Turn turn = buildTurn(5L, TurnStatus.TURN_PENDING, patient, schedule);

    when(turnRepository.findById(5L)).thenReturn(Optional.of(turn));
    when(turnRepository.save(any(Turn.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(turnRepository.existsByScheduleIdAndStatusNotAndIdNot(1L, TurnStatus.TURN_CANCELLED, 5L)).thenReturn(true);

    turnService.cancelTurn(5L, patientUuid.toString());

    verify(scheduleRepository, never()).saveAndFlush(any());
    verify(scheduleRepository, never()).save(any());
  }

  @Test
  void cancelTurn_doesNotResurrectAnUnavailableSchedule() {
    UUID patientUuid = UUID.randomUUID();
    Schedule schedule = buildSchedule(1L, buildDoctor(UUID.randomUUID()), buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_UNAVAILABLE);
    Patient patient = buildPatient(patientUuid);
    Turn turn = buildTurn(5L, TurnStatus.TURN_PENDING, patient, schedule);

    when(turnRepository.findById(5L)).thenReturn(Optional.of(turn));
    when(turnRepository.save(any(Turn.class))).thenAnswer(invocation -> invocation.getArgument(0));

    turnService.cancelTurn(5L, patientUuid.toString());

    // An admin-blocked slot must never be examined for release, let alone flipped to FREE.
    verify(turnRepository, never()).existsByScheduleIdAndStatusNotAndIdNot(anyLong(), any(), anyLong());
    verify(scheduleRepository, never()).saveAndFlush(any());
    assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.STATUS_UNAVAILABLE);
  }

  // -- mapToDTO nested-id enrichment (exercised via getById) ------------------

  @Test
  void getById_populatesNestedServiceDoctorStablishmentIdsAndPatientUuidAndPhone() {
    UUID doctorUuid = UUID.randomUUID();
    UUID patientUuid = UUID.randomUUID();
    Doctor doctor = buildDoctor(doctorUuid);
    Servicio service = buildService(10L);
    Stablishment stablishment = buildStablishment(20L);
    Schedule schedule = buildSchedule(1L, doctor, service, stablishment, ScheduleStatus.STATUS_OCCUPIED);
    Patient patient = buildPatient(patientUuid);
    Turn turn = buildTurn(1L, TurnStatus.TURN_PENDING, patient, schedule);

    when(turnRepository.findById(1L)).thenReturn(Optional.of(turn));

    TurnDTO dto = turnService.getById(1L);

    // Without service.id, the Angular reassign picker cannot scope schedules
    // by service and silently shows slots from every service.
    assertThat(dto.getSchedule().getService().getId()).isEqualTo(10L);
    assertThat(dto.getSchedule().getDoctor().getUuid()).isEqualTo(doctorUuid);
    assertThat(dto.getSchedule().getStablishment().getId()).isEqualTo(20L);
    // Exposed so staff can book a follow-up turn for this patient via
    // POST /api/turns/staff, which requires patient.uuid in the payload.
    assertThat(dto.getPatient().getUuid()).isEqualTo(patientUuid);
    assertThat(dto.getPatient().getPhone()).isEqualTo("0999999999");
  }

  @Test
  void getById_neverPopulatesPatientPassword_evenThoughTheEntityHasOne() {
    Patient patient = buildPatient(UUID.randomUUID());
    patient.setPassword("super-secret-hash");
    Turn turn = buildTurn(1L, TurnStatus.TURN_PENDING, patient, null);

    when(turnRepository.findById(1L)).thenReturn(Optional.of(turn));

    TurnDTO dto = turnService.getById(1L);

    assertThat(dto.getPatient().getPassword()).isNull();
  }

  // -- reassignTurn: same-service guard, cross-doctor explicitly allowed ------

  @Test
  void reassignTurn_rejectsTargetFromADifferentService_evenWhenFree() {
    Doctor doctorA = buildDoctor(UUID.randomUUID());
    Servicio cardiology = buildService(1L);
    Servicio dermatology = buildService(2L);
    Schedule oldSchedule = buildSchedule(100L, doctorA, cardiology, buildStablishment(1L), ScheduleStatus.STATUS_OCCUPIED);
    Turn turn = buildTurn(5L, TurnStatus.TURN_PENDING, buildPatient(UUID.randomUUID()), oldSchedule);

    Schedule targetSchedule = buildSchedule(
        200L, buildDoctor(UUID.randomUUID()), dermatology, buildStablishment(1L), ScheduleStatus.STATUS_FREE);

    when(turnRepository.findById(5L)).thenReturn(Optional.of(turn));
    when(scheduleRepository.findById(200L)).thenReturn(Optional.of(targetSchedule));

    assertThatThrownBy(() -> turnService.reassignTurn(5L, 200L, UUID.randomUUID().toString()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("servicio distinto");

    verify(turnRepository, never()).save(any());
  }

  @Test
  void reassignTurn_allowsTargetWithADifferentDoctor_whenServiceIsTheSame() {
    Servicio cardiology = buildService(1L);
    Doctor originalDoctor = buildDoctor(UUID.randomUUID());
    Doctor otherDoctor = buildDoctor(UUID.randomUUID());
    Schedule oldSchedule = buildSchedule(100L, originalDoctor, cardiology, buildStablishment(1L), ScheduleStatus.STATUS_OCCUPIED);
    Turn turn = buildTurn(5L, TurnStatus.TURN_PENDING, buildPatient(UUID.randomUUID()), oldSchedule);

    Schedule targetSchedule = buildSchedule(200L, otherDoctor, cardiology, buildStablishment(1L), ScheduleStatus.STATUS_FREE);

    when(turnRepository.findById(5L)).thenReturn(Optional.of(turn));
    when(scheduleRepository.findById(200L)).thenReturn(Optional.of(targetSchedule));
    when(turnRepository.countTurnsByServiceAndDate(1L, targetSchedule.getDate())).thenReturn(0L);
    when(turnRepository.save(any(Turn.class))).thenAnswer(invocation -> invocation.getArgument(0));

    TurnDTO result = turnService.reassignTurn(5L, 200L, UUID.randomUUID().toString());

    assertThat(result.getSchedule().getId()).isEqualTo(200L);
    assertThat(result.getSchedule().getDoctor().getUuid()).isEqualTo(otherDoctor.getUuid());
    assertThat(result.getStatus()).isEqualTo(TurnStatus.TURN_PENDING);
  }

  // -- reassignTurn: schedule occupancy — releases old, occupies new ----------

  @Test
  void reassignTurn_releasesOldSchedule_andOccupiesNewSchedule() {
    Servicio cardiology = buildService(1L);
    Doctor originalDoctor = buildDoctor(UUID.randomUUID());
    Doctor otherDoctor = buildDoctor(UUID.randomUUID());
    Schedule oldSchedule = buildSchedule(100L, originalDoctor, cardiology, buildStablishment(1L), ScheduleStatus.STATUS_OCCUPIED);
    Turn turn = buildTurn(5L, TurnStatus.TURN_PENDING, buildPatient(UUID.randomUUID()), oldSchedule);
    Schedule targetSchedule = buildSchedule(200L, otherDoctor, cardiology, buildStablishment(1L), ScheduleStatus.STATUS_FREE);

    when(turnRepository.findById(5L)).thenReturn(Optional.of(turn));
    when(scheduleRepository.findById(200L)).thenReturn(Optional.of(targetSchedule));
    when(turnRepository.countTurnsByServiceAndDate(1L, targetSchedule.getDate())).thenReturn(0L);
    when(turnRepository.save(any(Turn.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(scheduleRepository.saveAndFlush(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(turnRepository.existsByScheduleIdAndStatusNotAndIdNot(100L, TurnStatus.TURN_CANCELLED, 5L)).thenReturn(false);

    turnService.reassignTurn(5L, 200L, UUID.randomUUID().toString());

    assertThat(targetSchedule.getStatus()).isEqualTo(ScheduleStatus.STATUS_OCCUPIED);
    assertThat(oldSchedule.getStatus()).isEqualTo(ScheduleStatus.STATUS_FREE);
    verify(scheduleRepository, times(2)).saveAndFlush(any(Schedule.class));
  }

  @Test
  void reassignTurn_rejectsWithSpanishMessage_whenTheNewScheduleWasJustTakenConcurrently() {
    Servicio cardiology = buildService(1L);
    Schedule oldSchedule = buildSchedule(100L, buildDoctor(UUID.randomUUID()), cardiology, buildStablishment(1L), ScheduleStatus.STATUS_OCCUPIED);
    Turn turn = buildTurn(5L, TurnStatus.TURN_PENDING, buildPatient(UUID.randomUUID()), oldSchedule);
    Schedule targetSchedule = buildSchedule(200L, buildDoctor(UUID.randomUUID()), cardiology, buildStablishment(1L), ScheduleStatus.STATUS_FREE);

    when(turnRepository.findById(5L)).thenReturn(Optional.of(turn));
    when(scheduleRepository.findById(200L)).thenReturn(Optional.of(targetSchedule));
    when(turnRepository.countTurnsByServiceAndDate(1L, targetSchedule.getDate())).thenReturn(0L);
    when(scheduleRepository.saveAndFlush(any(Schedule.class)))
        .thenThrow(new ObjectOptimisticLockingFailureException(Schedule.class, 200L));

    assertThatThrownBy(() -> turnService.reassignTurn(5L, 200L, UUID.randomUUID().toString()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("reservado");

    // The original turn must stay exactly as it was: never saved, old schedule untouched.
    verify(turnRepository, never()).save(any());
    assertThat(oldSchedule.getStatus()).isEqualTo(ScheduleStatus.STATUS_OCCUPIED);
  }

  // -- markAsTreated: new terminal-state guard, PENDING kept for compatibility -

  @Test
  void markAsTreated_rejectsATurnThatWasAlreadyTreated() {
    Doctor doctor = buildDoctor(UUID.randomUUID());
    Schedule schedule = buildSchedule(1L, doctor, buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_OCCUPIED);
    Turn turn = buildTurn(1L, TurnStatus.TURN_TREATED, buildPatient(UUID.randomUUID()), schedule);
    when(turnRepository.findById(1L)).thenReturn(Optional.of(turn));

    assertThatThrownBy(() -> turnService.markAsTreated(1L, doctor.getUuid().toString()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("atendido o cancelado");

    verify(turnRepository, never()).save(any());
  }

  @Test
  void markAsTreated_rejectsACancelledTurn() {
    Doctor doctor = buildDoctor(UUID.randomUUID());
    Schedule schedule = buildSchedule(1L, doctor, buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_OCCUPIED);
    Turn turn = buildTurn(1L, TurnStatus.TURN_CANCELLED, buildPatient(UUID.randomUUID()), schedule);
    when(turnRepository.findById(1L)).thenReturn(Optional.of(turn));

    assertThatThrownBy(() -> turnService.markAsTreated(1L, doctor.getUuid().toString()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("atendido o cancelado");

    verify(turnRepository, never()).save(any());
  }

  @Test
  void markAsTreated_stillAcceptsPendingDirectly_forBackwardCompatibility() {
    Doctor doctor = buildDoctor(UUID.randomUUID());
    Schedule schedule = buildSchedule(1L, doctor, buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_OCCUPIED);
    Turn turn = buildTurn(1L, TurnStatus.TURN_PENDING, buildPatient(UUID.randomUUID()), schedule);
    when(turnRepository.findById(1L)).thenReturn(Optional.of(turn));
    when(turnRepository.save(any(Turn.class))).thenAnswer(invocation -> invocation.getArgument(0));

    TurnDTO result = turnService.markAsTreated(1L, doctor.getUuid().toString());

    assertThat(result.getStatus()).isEqualTo(TurnStatus.TURN_TREATED);
  }

  @Test
  void markAsTreated_acceptsFromWaitingAndFromInTreatment() {
    Doctor doctor = buildDoctor(UUID.randomUUID());
    when(turnRepository.save(any(Turn.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Schedule waitingSchedule = buildSchedule(1L, doctor, buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_OCCUPIED);
    Turn waitingTurn = buildTurn(1L, TurnStatus.TURN_WAITNG, buildPatient(UUID.randomUUID()), waitingSchedule);
    when(turnRepository.findById(1L)).thenReturn(Optional.of(waitingTurn));
    assertThat(turnService.markAsTreated(1L, doctor.getUuid().toString()).getStatus()).isEqualTo(TurnStatus.TURN_TREATED);

    Schedule inTreatmentSchedule = buildSchedule(2L, doctor, buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_OCCUPIED);
    Turn inTreatmentTurn = buildTurn(2L, TurnStatus.TURN_IN_TREATMENT, buildPatient(UUID.randomUUID()), inTreatmentSchedule);
    when(turnRepository.findById(2L)).thenReturn(Optional.of(inTreatmentTurn));
    assertThat(turnService.markAsTreated(2L, doctor.getUuid().toString()).getStatus()).isEqualTo(TurnStatus.TURN_TREATED);
  }

  // -- markAsWaiting: TURN_PENDING -> TURN_WAITNG (check-in) -------------------

  @Test
  void markAsWaiting_movesAPendingTurnIntoTheWaitingRoom() {
    Turn turn = buildTurn(1L, TurnStatus.TURN_PENDING, buildPatient(UUID.randomUUID()), null);
    when(turnRepository.findById(1L)).thenReturn(Optional.of(turn));
    when(turnRepository.save(any(Turn.class))).thenAnswer(invocation -> invocation.getArgument(0));

    TurnDTO result = turnService.markAsWaiting(1L, UUID.randomUUID().toString());

    assertThat(result.getStatus()).isEqualTo(TurnStatus.TURN_WAITNG);
  }

  @Test
  void markAsWaiting_rejectsATurnThatIsNotPending() {
    Turn turn = buildTurn(1L, TurnStatus.TURN_CANCELLED, buildPatient(UUID.randomUUID()), null);
    when(turnRepository.findById(1L)).thenReturn(Optional.of(turn));

    assertThatThrownBy(() -> turnService.markAsWaiting(1L, UUID.randomUUID().toString()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("pendiente");

    verify(turnRepository, never()).save(any());
  }

  @Test
  void markAsWaiting_broadcastsTheUpdateToEstablishmentDoctorAndPatientChannels() {
    Doctor doctor = buildDoctor(UUID.randomUUID());
    Patient patient = buildPatient(UUID.randomUUID());
    Schedule schedule = buildSchedule(1L, doctor, buildService(1L), buildStablishment(1L), ScheduleStatus.STATUS_OCCUPIED);
    Turn turn = buildTurn(1L, TurnStatus.TURN_PENDING, patient, schedule);
    when(turnRepository.findById(1L)).thenReturn(Optional.of(turn));
    when(turnRepository.save(any(Turn.class))).thenAnswer(invocation -> invocation.getArgument(0));

    turnService.markAsWaiting(1L, UUID.randomUUID().toString());

    // Establishment/waiting-room channel: anonymous board payload only (no patient data).
    verify(messagingTemplate).convertAndSend(anyString(), any(TurnBoardDTO.class));
    // Doctor's own channel: full detail (pre-existing, proven mechanism).
    verify(messagingTemplate).convertAndSendToUser(eq(doctor.getUuid().toString()), anyString(), any(TurnDTO.class));
    // Patient's own channel: full detail of their OWN turn, same mechanism as the doctor channel.
    verify(messagingTemplate).convertAndSendToUser(eq(patient.getUuid().toString()), eq("/topic/turns"), any(TurnDTO.class));
  }

  // -- markAsInTreatment: TURN_WAITNG -> TURN_IN_TREATMENT (start attention) ---

  @Test
  void markAsInTreatment_movesAWaitingTurnIntoTreatment() {
    Turn turn = buildTurn(1L, TurnStatus.TURN_WAITNG, buildPatient(UUID.randomUUID()), null);
    when(turnRepository.findById(1L)).thenReturn(Optional.of(turn));
    when(turnRepository.save(any(Turn.class))).thenAnswer(invocation -> invocation.getArgument(0));

    TurnDTO result = turnService.markAsInTreatment(1L, UUID.randomUUID().toString());

    assertThat(result.getStatus()).isEqualTo(TurnStatus.TURN_IN_TREATMENT);
  }

  @Test
  void markAsInTreatment_rejectsATurnThatIsStillPending() {
    Turn turn = buildTurn(1L, TurnStatus.TURN_PENDING, buildPatient(UUID.randomUUID()), null);
    when(turnRepository.findById(1L)).thenReturn(Optional.of(turn));

    assertThatThrownBy(() -> turnService.markAsInTreatment(1L, UUID.randomUUID().toString()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("sala de espera");

    verify(turnRepository, never()).save(any());
  }

  @Test
  void markAsInTreatment_rejectsATurnAlreadyInTreatment() {
    Turn turn = buildTurn(1L, TurnStatus.TURN_IN_TREATMENT, buildPatient(UUID.randomUUID()), null);
    when(turnRepository.findById(1L)).thenReturn(Optional.of(turn));

    assertThatThrownBy(() -> turnService.markAsInTreatment(1L, UUID.randomUUID().toString()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("sala de espera");

    verify(turnRepository, never()).save(any());
  }

  @Test
  void markAsInTreatment_notifiesThePatientsOwnChannel_evenWithoutASchedule() {
    // schedule is null on purpose: the establishment/doctor channels are
    // schedule-dependent and skip themselves, but the patient's own channel
    // must not depend on the schedule being present.
    Patient patient = buildPatient(UUID.randomUUID());
    Turn turn = buildTurn(1L, TurnStatus.TURN_WAITNG, patient, null);
    when(turnRepository.findById(1L)).thenReturn(Optional.of(turn));
    when(turnRepository.save(any(Turn.class))).thenAnswer(invocation -> invocation.getArgument(0));

    turnService.markAsInTreatment(1L, UUID.randomUUID().toString());

    verify(messagingTemplate).convertAndSendToUser(eq(patient.getUuid().toString()), eq("/topic/turns"), any(TurnDTO.class));
  }
}
