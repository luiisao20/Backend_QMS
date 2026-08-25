package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.devluis.entity.Doctor;
import com.devluis.entity.Schedule;
import com.devluis.entity.ScheduleTemplate;
import com.devluis.entity.Servicio;
import com.devluis.entity.Stablishment;
import com.devluis.repository.DoctorRepository;
import com.devluis.repository.HolidayRepository;
import com.devluis.repository.ScheduleRepository;
import com.devluis.repository.ScheduleTemplateRepository;
import com.devluis.repository.ServiceRepository;
import com.devluis.repository.StablishmentRepository;
import com.devluis.repository.TimeOffRepository;
import com.devluis.repository.TurnRepository;
import com.devluis.types.GenerateSchedulesBody;
import com.devluis.types.GenerateSchedulesFromTemplateBody;
import com.devluis.types.ScheduleStatus;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

  @Mock
  private ScheduleRepository scheduleRepository;
  @Mock
  private DoctorRepository doctorRepository;
  @Mock
  private ServiceRepository serviceRepository;
  @Mock
  private StablishmentRepository stablishmentRepository;
  @Mock
  private TurnRepository turnRepository;
  @Mock
  private HolidayRepository holidayRepository;
  @Mock
  private TimeOffRepository timeOffRepository;
  @Mock
  private ScheduleTemplateRepository scheduleTemplateRepository;

  private ScheduleService scheduleService;

  @BeforeEach
  void setUp() {
    scheduleService = new ScheduleService(
        scheduleRepository, doctorRepository, serviceRepository, stablishmentRepository, turnRepository,
        holidayRepository, timeOffRepository, scheduleTemplateRepository);
  }

  // -- delete() guard: a Turn is never disposable --------------------------

  @Test
  void delete_removesSchedule_whenNoTurnsAreBooked() {
    when(scheduleRepository.existsById(1L)).thenReturn(true);
    when(turnRepository.existsByScheduleId(1L)).thenReturn(false);

    scheduleService.delete(1L);

    verify(scheduleRepository).deleteById(1L);
  }

  @Test
  void delete_throwsClearSpanishMessage_whenScheduleHasBookedTurns() {
    when(scheduleRepository.existsById(1L)).thenReturn(true);
    when(turnRepository.existsByScheduleId(1L)).thenReturn(true);

    assertThatThrownBy(() -> scheduleService.delete(1L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("turnos");

    verify(scheduleRepository, never()).deleteById(any());
  }

  @Test
  void delete_throws_whenScheduleDoesNotExist() {
    when(scheduleRepository.existsById(1L)).thenReturn(false);

    assertThatThrownBy(() -> scheduleService.delete(1L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Horario no encontrado");

    verify(turnRepository, never()).existsByScheduleId(any());
    verify(scheduleRepository, never()).deleteById(any());
  }

  // -- getAll() Specification: serviceId / from / to / status filters ------

  @SuppressWarnings("unchecked")
  private ArgumentCaptor<Specification<Schedule>> captureSpecification() {
    // The service replaces an unsorted Pageable with a default date/hour sort
    // before calling the repository, so we match on "any Pageable" here — the
    // sort-defaulting behavior is pre-existing and not what these tests cover.
    when(scheduleRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
    return ArgumentCaptor.forClass(Specification.class);
  }

  @Test
  void getAll_withOnlyDateFilter_appliesExactDateEquality() {
    Pageable pageable = PageRequest.of(0, 10);
    ArgumentCaptor<Specification<Schedule>> captor = captureSpecification();
    LocalDate date = LocalDate.of(2026, 3, 10);

    scheduleService.getAll(date, null, null, null, null, null, null, null, pageable);

    verify(scheduleRepository).findAll(captor.capture(), any(Pageable.class));

    Root<Schedule> root = mock(Root.class, RETURNS_DEEP_STUBS);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

    captor.getValue().toPredicate(root, query, cb);

    verify(cb).equal(root.get("date"), date);
  }

  @Test
  void getAll_withFromAndTo_appliesDateRangeInsteadOfExactMatch() {
    Pageable pageable = PageRequest.of(0, 10);
    ArgumentCaptor<Specification<Schedule>> captor = captureSpecification();
    LocalDate from = LocalDate.of(2026, 3, 1);
    LocalDate to = LocalDate.of(2026, 3, 31);

    scheduleService.getAll(null, null, null, null, null, from, to, null, pageable);

    verify(scheduleRepository).findAll(captor.capture(), any(Pageable.class));

    Root<Schedule> root = mock(Root.class, RETURNS_DEEP_STUBS);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

    captor.getValue().toPredicate(root, query, cb);

    verify(cb).greaterThanOrEqualTo(root.get("date"), from);
    verify(cb).lessThanOrEqualTo(root.get("date"), to);
  }

  @Test
  void getAll_withDateAndFromTo_appliesBothAsAdditiveAndPredicates() {
    // Documents the deliberate design choice: `date` and `from`/`to` are
    // independent, additive (AND) filters, exactly like every other filter in
    // this Specification. No client sends both today (Angular admin sends
    // only `date`; the Flutter app sends only `from`/`to`), but if one ever
    // did, the result is the intersection, not an error.
    Pageable pageable = PageRequest.of(0, 10);
    ArgumentCaptor<Specification<Schedule>> captor = captureSpecification();
    LocalDate date = LocalDate.of(2026, 3, 15);
    LocalDate from = LocalDate.of(2026, 3, 1);
    LocalDate to = LocalDate.of(2026, 3, 31);

    scheduleService.getAll(date, null, null, null, null, from, to, null, pageable);

    verify(scheduleRepository).findAll(captor.capture(), any(Pageable.class));

    Root<Schedule> root = mock(Root.class, RETURNS_DEEP_STUBS);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

    captor.getValue().toPredicate(root, query, cb);

    verify(cb).equal(root.get("date"), date);
    verify(cb).greaterThanOrEqualTo(root.get("date"), from);
    verify(cb).lessThanOrEqualTo(root.get("date"), to);
  }

  @Test
  void getAll_withServiceIdAndStatus_appliesEqualityOnNestedServiceAndStatus() {
    Pageable pageable = PageRequest.of(0, 10);
    ArgumentCaptor<Specification<Schedule>> captor = captureSpecification();
    Long serviceId = 9L;

    scheduleService.getAll(null, null, null, null, serviceId, null, null, ScheduleStatus.STATUS_FREE, pageable);

    verify(scheduleRepository).findAll(captor.capture(), any(Pageable.class));

    Root<Schedule> root = mock(Root.class, RETURNS_DEEP_STUBS);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

    captor.getValue().toPredicate(root, query, cb);

    verify(cb).equal(root.get("service").get("id"), serviceId);
    verify(cb).equal(root.get("status"), ScheduleStatus.STATUS_FREE);
  }

  @Test
  void getAll_withNoFilters_behavesLikeTheOldUnfilteredListing() {
    // Backward compatibility: none of the new params should force any
    // predicate to be built when they are all null.
    Pageable pageable = PageRequest.of(0, 10);
    ArgumentCaptor<Specification<Schedule>> captor = captureSpecification();

    scheduleService.getAll(pageable);

    verify(scheduleRepository).findAll(captor.capture(), any(Pageable.class));

    Root<Schedule> root = mock(Root.class, RETURNS_DEEP_STUBS);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

    captor.getValue().toPredicate(root, query, cb);

    verify(cb, never()).equal(any(), any());
    verify(cb, never()).greaterThanOrEqualTo(any(), any(LocalDate.class));
    verify(cb, never()).lessThanOrEqualTo(any(), any(LocalDate.class));
  }

  // -- generateSchedules(): "bloqueo de citas" integration -----------------
  // A holiday or a doctor's time-off must SKIP generation entirely for that
  // date, instead of silently creating slots nobody should be able to book.

  private GenerateSchedulesBody bodyFor(Long serviceId, Long stablishmentId, UUID doctorId, LocalDate date) {
    GenerateSchedulesBody body = new GenerateSchedulesBody();
    body.setServiceId(serviceId);
    body.setStablishmentId(stablishmentId);
    body.setDoctorId(doctorId);
    body.setDate(date);
    body.setIntervalMinutes(60);
    return body;
  }

  @Test
  void generateSchedules_throws_whenDateIsAHolidayForTheStablishment() {
    Servicio servicio = Servicio.builder().id(1L).build();
    Stablishment stablishment = Stablishment.builder().id(2L).services(List.of(servicio)).build();
    LocalDate date = LocalDate.of(2026, 12, 25);
    GenerateSchedulesBody body = bodyFor(1L, 2L, null, date);

    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio));
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment));
    when(holidayRepository.existsApplicableHoliday(date, 2L)).thenReturn(true);

    assertThatThrownBy(() -> scheduleService.generateSchedules(body))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("feriado");

    verify(scheduleRepository, never()).saveAll(any());
    verify(timeOffRepository, never())
        .existsByDoctorUuidAndStartDateLessThanEqualAndEndDateGreaterThanEqual(any(), any(), any());
  }

  @Test
  void generateSchedules_throws_whenDoctorHasATimeOffCoveringTheDate() {
    UUID doctorUuid = UUID.randomUUID();
    Servicio servicio = Servicio.builder().id(1L).build();
    Stablishment stablishment = Stablishment.builder().id(2L).services(List.of(servicio)).build();
    Doctor doctor = Doctor.builder().uuid(doctorUuid).services(List.of(servicio)).stablishments(List.of(stablishment)).build();
    LocalDate date = LocalDate.of(2026, 9, 10);
    GenerateSchedulesBody body = bodyFor(1L, 2L, doctorUuid, date);

    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio));
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment));
    when(doctorRepository.findById(doctorUuid)).thenReturn(Optional.of(doctor));
    when(holidayRepository.existsApplicableHoliday(date, 2L)).thenReturn(false);
    when(timeOffRepository.existsByDoctorUuidAndStartDateLessThanEqualAndEndDateGreaterThanEqual(doctorUuid, date, date))
        .thenReturn(true);

    assertThatThrownBy(() -> scheduleService.generateSchedules(body))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("ausencia");

    verify(scheduleRepository, never()).saveAll(any());
  }

  @Test
  void generateSchedules_skipsTheTimeOffCheck_whenNoDoctorIsSpecified() {
    Servicio servicio = Servicio.builder().id(1L).build();
    Stablishment stablishment = Stablishment.builder().id(2L).services(List.of(servicio)).build();
    LocalDate date = LocalDate.of(2026, 9, 10);
    GenerateSchedulesBody body = bodyFor(1L, 2L, null, date);

    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio));
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment));
    when(holidayRepository.existsApplicableHoliday(date, 2L)).thenReturn(false);
    when(scheduleRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

    scheduleService.generateSchedules(body);

    verify(timeOffRepository, never())
        .existsByDoctorUuidAndStartDateLessThanEqualAndEndDateGreaterThanEqual(any(), any(), any());
  }

  // -- generateSchedulesFromTemplates(): template-driven period generation --
  // Reads the applicable ScheduleTemplate per date instead of requiring the
  // admin to re-type start/end/interval — see ScheduleTemplate's docblock.
  // Per-date blockers (holiday, doctor time-off, no applicable template) are
  // SKIPPED, not thrown, so a whole period can be generated in one call even
  // if some individual days must be excluded. generateSchedules(...) above
  // is untouched and keeps working exactly as before this addition.

  private GenerateSchedulesFromTemplateBody periodBodyFor(Long serviceId, Long stablishmentId, UUID doctorId,
      LocalDate from, LocalDate to) {
    GenerateSchedulesFromTemplateBody body = new GenerateSchedulesFromTemplateBody();
    body.setServiceId(serviceId);
    body.setStablishmentId(stablishmentId);
    body.setDoctorId(doctorId);
    body.setFrom(from);
    body.setTo(to);
    return body;
  }

  private ScheduleTemplate poolTemplate(LocalTime start, LocalTime end, int interval) {
    return ScheduleTemplate.builder()
        .id(1L).startTime(start).endTime(end).slotIntervalMinutes(interval).build();
  }

  @Test
  void generateSchedulesFromTemplates_generatesSlots_atTheTemplateInterval_forASingleApplicableDate() {
    Servicio servicio = Servicio.builder().id(1L).build();
    Stablishment stablishment = Stablishment.builder().id(2L).services(List.of(servicio)).build();
    LocalDate monday = LocalDate.of(2026, 9, 7);
    assertThat(monday.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    ScheduleTemplate template = poolTemplate(LocalTime.of(8, 0), LocalTime.of(9, 0), 30);

    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio));
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment));
    when(holidayRepository.existsApplicableHoliday(monday, 2L)).thenReturn(false);
    when(scheduleTemplateRepository.findApplicable(2L, 1L, null, DayOfWeek.MONDAY, monday))
        .thenReturn(Optional.of(template));
    when(scheduleRepository.existsByServiceIdAndStablishmentIdAndDateAndHour(eq(1L), eq(2L), eq(monday), any()))
        .thenReturn(false);
    when(scheduleRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

    List<com.devluis.dto.ScheduleDTO> result = scheduleService.generateSchedulesFromTemplates(
        periodBodyFor(1L, 2L, null, monday, monday));

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getHour()).isEqualTo(LocalTime.of(8, 0));
    assertThat(result.get(1).getHour()).isEqualTo(LocalTime.of(8, 30));
    verify(timeOffRepository, never())
        .existsByDoctorUuidAndStartDateLessThanEqualAndEndDateGreaterThanEqual(any(), any(), any());
  }

  @Test
  void generateSchedulesFromTemplates_skipsDatesWithNoApplicableTemplate_butKeepsOthersInRange() {
    Servicio servicio = Servicio.builder().id(1L).build();
    Stablishment stablishment = Stablishment.builder().id(2L).services(List.of(servicio)).build();
    LocalDate monday = LocalDate.of(2026, 9, 7);
    LocalDate tuesday = monday.plusDays(1);
    ScheduleTemplate mondayTemplate = poolTemplate(LocalTime.of(8, 0), LocalTime.of(9, 0), 60);

    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio));
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment));
    when(holidayRepository.existsApplicableHoliday(any(), eq(2L))).thenReturn(false);
    when(scheduleTemplateRepository.findApplicable(2L, 1L, null, DayOfWeek.MONDAY, monday))
        .thenReturn(Optional.of(mondayTemplate));
    when(scheduleTemplateRepository.findApplicable(2L, 1L, null, DayOfWeek.TUESDAY, tuesday))
        .thenReturn(Optional.empty());
    when(scheduleRepository.existsByServiceIdAndStablishmentIdAndDateAndHour(eq(1L), eq(2L), eq(monday), any()))
        .thenReturn(false);
    when(scheduleRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

    List<com.devluis.dto.ScheduleDTO> result = scheduleService.generateSchedulesFromTemplates(
        periodBodyFor(1L, 2L, null, monday, tuesday));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getDate()).isEqualTo(monday);
  }

  @Test
  void generateSchedulesFromTemplates_skipsHolidayDates_sameAsParameterDrivenPath() {
    Servicio servicio = Servicio.builder().id(1L).build();
    Stablishment stablishment = Stablishment.builder().id(2L).services(List.of(servicio)).build();
    LocalDate holiday = LocalDate.of(2026, 12, 25);

    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio));
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment));
    when(holidayRepository.existsApplicableHoliday(holiday, 2L)).thenReturn(true);

    assertThatThrownBy(() -> scheduleService.generateSchedulesFromTemplates(
        periodBodyFor(1L, 2L, null, holiday, holiday)))
        .isInstanceOf(RuntimeException.class);

    verify(scheduleTemplateRepository, never()).findApplicable(any(), any(), any(), any(), any());
    verify(scheduleRepository, never()).saveAll(any());
  }

  @Test
  void generateSchedulesFromTemplates_skipsDoctorTimeOffDates_sameAsParameterDrivenPath() {
    UUID doctorUuid = UUID.randomUUID();
    Servicio servicio = Servicio.builder().id(1L).build();
    Stablishment stablishment = Stablishment.builder().id(2L).services(List.of(servicio)).build();
    Doctor doctor = Doctor.builder().uuid(doctorUuid).services(List.of(servicio)).stablishments(List.of(stablishment)).build();
    LocalDate date = LocalDate.of(2026, 9, 10);

    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio));
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment));
    when(doctorRepository.findById(doctorUuid)).thenReturn(Optional.of(doctor));
    when(holidayRepository.existsApplicableHoliday(date, 2L)).thenReturn(false);
    when(timeOffRepository.existsByDoctorUuidAndStartDateLessThanEqualAndEndDateGreaterThanEqual(doctorUuid, date, date))
        .thenReturn(true);

    assertThatThrownBy(() -> scheduleService.generateSchedulesFromTemplates(
        periodBodyFor(1L, 2L, doctorUuid, date, date)))
        .isInstanceOf(RuntimeException.class);

    verify(scheduleTemplateRepository, never()).findApplicable(any(), any(), any(), any(), any());
  }

  @Test
  void generateSchedulesFromTemplates_throws_whenToIsBeforeFrom() {
    LocalDate from = LocalDate.of(2026, 9, 10);
    LocalDate to = LocalDate.of(2026, 9, 1);

    assertThatThrownBy(() -> scheduleService.generateSchedulesFromTemplates(
        periodBodyFor(1L, 2L, null, from, to)))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("período");

    verify(serviceRepository, never()).findById(any());
  }

  @Test
  void generateSchedulesFromTemplates_throws_whenNothingCouldBeGeneratedInTheWholePeriod() {
    Servicio servicio = Servicio.builder().id(1L).build();
    Stablishment stablishment = Stablishment.builder().id(2L).services(List.of(servicio)).build();
    LocalDate date = LocalDate.of(2026, 9, 7);

    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio));
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment));
    when(holidayRepository.existsApplicableHoliday(date, 2L)).thenReturn(false);
    when(scheduleTemplateRepository.findApplicable(2L, 1L, null, date.getDayOfWeek(), date))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> scheduleService.generateSchedulesFromTemplates(
        periodBodyFor(1L, 2L, null, date, date)))
        .isInstanceOf(RuntimeException.class);

    verify(scheduleRepository, never()).saveAll(any());
  }
}
