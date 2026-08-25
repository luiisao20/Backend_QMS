package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

import com.devluis.dto.DoctorDTO;
import com.devluis.dto.ScheduleTemplateDTO;
import com.devluis.dto.ServicioDTO;
import com.devluis.dto.StablishmentDTO;
import com.devluis.entity.Doctor;
import com.devluis.entity.ScheduleTemplate;
import com.devluis.entity.Servicio;
import com.devluis.entity.Stablishment;
import com.devluis.repository.DoctorRepository;
import com.devluis.repository.ScheduleTemplateRepository;
import com.devluis.repository.ServiceRepository;
import com.devluis.repository.StablishmentRepository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

/**
 * Overlap rule under test (see ScheduleTemplate's docblock): a create/update
 * is REJECTED outright when it would produce a contradictory weekly pattern —
 * same precedent as Promotion, split into two scopes that mirror
 * ScheduleService's own two "is this slot taken" checks:
 * - doctor-bound templates: at most one per (doctor, dayOfWeek) with
 * overlapping [startTime, endTime) AND overlapping validity windows,
 * regardless of establishment/service.
 * - pool templates (doctor == null): at most one per (stablishment,
 * servicio, dayOfWeek) with the same time/validity overlap rule.
 * <p>
 * Pre-existing-slots decision under test (see docblock): update()/delete()
 * never touch Schedule rows — a ScheduleTemplate is a pure generator input.
 * This service has NO dependency on ScheduleRepository/ScheduleBlockingSupport
 * at all, unlike HolidayService/TimeOffService — that absence IS the
 * decision, not an oversight.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleTemplateServiceTest {

  @Mock
  private ScheduleTemplateRepository scheduleTemplateRepository;
  @Mock
  private StablishmentRepository stablishmentRepository;
  @Mock
  private ServiceRepository serviceRepository;
  @Mock
  private DoctorRepository doctorRepository;

  private ScheduleTemplateService scheduleTemplateService;

  @BeforeEach
  void setUp() {
    scheduleTemplateService = new ScheduleTemplateService(
        scheduleTemplateRepository, stablishmentRepository, serviceRepository, doctorRepository);
  }

  private Servicio servicio() {
    return Servicio.builder().id(1L).name("Consulta general").price(50f).build();
  }

  private Stablishment stablishment() {
    return Stablishment.builder().id(2L).name("Sede Norte").services(List.of(servicio())).build();
  }

  private Doctor doctor(UUID uuid) {
    return Doctor.builder().uuid(uuid).firstName("Ana").lastName("Pérez")
        .services(List.of(servicio())).stablishments(List.of(stablishment())).build();
  }

  private ScheduleTemplateDTO poolDto(DayOfWeek day, LocalTime start, LocalTime end) {
    return ScheduleTemplateDTO.builder()
        .stablishment(StablishmentDTO.builder().id(2L).build())
        .servicio(ServicioDTO.builder().id(1L).build())
        .dayOfWeek(day)
        .startTime(start)
        .endTime(end)
        .slotIntervalMinutes(30)
        .validFrom(LocalDate.of(2026, 1, 1))
        .build();
  }

  private ScheduleTemplateDTO doctorDto(UUID doctorUuid, DayOfWeek day, LocalTime start, LocalTime end) {
    ScheduleTemplateDTO dto = poolDto(day, start, end);
    dto.setDoctor(DoctorDTO.builder().uuid(doctorUuid).build());
    return dto;
  }

  // -- create() ---------------------------------------------------------

  @Test
  void create_savesPoolTemplate_whenNoOverlap() {
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment()));
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));
    when(scheduleTemplateRepository.existsOverlappingForPool(
        eq(2L), eq(1L), eq(DayOfWeek.MONDAY), any(), any(), any(), any(), isNull())).thenReturn(false);
    when(scheduleTemplateRepository.save(any(ScheduleTemplate.class))).thenAnswer(inv -> {
      ScheduleTemplate t = inv.getArgument(0);
      t.setId(10L);
      return t;
    });

    ScheduleTemplateDTO result = scheduleTemplateService.create(
        poolDto(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)));

    assertThat(result.getId()).isEqualTo(10L);
    assertThat(result.getDoctor()).isNull();
    assertThat(result.getStablishment().getId()).isEqualTo(2L);
    verify(scheduleTemplateRepository, never())
        .existsOverlappingForDoctor(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void create_savesDoctorTemplate_whenDoctorHasServiceAndStablishmentAssigned() {
    UUID doctorUuid = UUID.randomUUID();
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment()));
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));
    when(doctorRepository.findById(doctorUuid)).thenReturn(Optional.of(doctor(doctorUuid)));
    when(scheduleTemplateRepository.existsOverlappingForDoctor(
        eq(doctorUuid), eq(DayOfWeek.MONDAY), any(), any(), any(), any(), isNull())).thenReturn(false);
    when(scheduleTemplateRepository.save(any(ScheduleTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

    ScheduleTemplateDTO result = scheduleTemplateService.create(
        doctorDto(doctorUuid, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)));

    assertThat(result.getDoctor().getUuid()).isEqualTo(doctorUuid);
    verify(scheduleTemplateRepository, never())
        .existsOverlappingForPool(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void create_throws_whenStablishmentNotFound() {
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> scheduleTemplateService.create(
        poolDto(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Establecimiento no encontrado");

    verify(scheduleTemplateRepository, never()).save(any());
  }

  @Test
  void create_throws_whenServicioNotFound() {
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment()));
    when(serviceRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> scheduleTemplateService.create(
        poolDto(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Servicio no encontrado");

    verify(scheduleTemplateRepository, never()).save(any());
  }

  @Test
  void create_throws_whenDoctorNotFound() {
    UUID doctorUuid = UUID.randomUUID();
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment()));
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));
    when(doctorRepository.findById(doctorUuid)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> scheduleTemplateService.create(
        doctorDto(doctorUuid, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Doctor no encontrado");

    verify(scheduleTemplateRepository, never()).save(any());
  }

  @Test
  void create_throws_whenServiceNotAvailableInStablishment() {
    Stablishment stablishmentSinServicio = Stablishment.builder().id(2L).name("Sede Norte").services(List.of()).build();
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishmentSinServicio));
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));

    assertThatThrownBy(() -> scheduleTemplateService.create(
        poolDto(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no está disponible en este establecimiento");

    verify(scheduleTemplateRepository, never()).save(any());
  }

  @Test
  void create_throws_whenDoctorDoesNotHaveServiceAssigned() {
    UUID doctorUuid = UUID.randomUUID();
    Doctor doctorSinServicio = Doctor.builder().uuid(doctorUuid).services(List.of())
        .stablishments(List.of(stablishment())).build();
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment()));
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));
    when(doctorRepository.findById(doctorUuid)).thenReturn(Optional.of(doctorSinServicio));

    assertThatThrownBy(() -> scheduleTemplateService.create(
        doctorDto(doctorUuid, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no tiene asignado este servicio");

    verify(scheduleTemplateRepository, never()).save(any());
  }

  @Test
  void create_throws_whenDoctorNotAssignedToStablishment() {
    UUID doctorUuid = UUID.randomUUID();
    Doctor doctorSinSede = Doctor.builder().uuid(doctorUuid).services(List.of(servicio())).stablishments(List.of()).build();
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment()));
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));
    when(doctorRepository.findById(doctorUuid)).thenReturn(Optional.of(doctorSinSede));

    assertThatThrownBy(() -> scheduleTemplateService.create(
        doctorDto(doctorUuid, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no está asignado a este establecimiento");

    verify(scheduleTemplateRepository, never()).save(any());
  }

  @Test
  void create_throws_whenEndTimeIsNotAfterStartTime() {
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment()));
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));

    assertThatThrownBy(() -> scheduleTemplateService.create(
        poolDto(DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(12, 0))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("hora de fin");

    verify(scheduleTemplateRepository, never()).save(any());
  }

  @Test
  void create_throws_whenValidUntilIsBeforeValidFrom() {
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment()));
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));
    ScheduleTemplateDTO dto = poolDto(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
    dto.setValidFrom(LocalDate.of(2026, 6, 1));
    dto.setValidUntil(LocalDate.of(2026, 1, 1));

    assertThatThrownBy(() -> scheduleTemplateService.create(dto))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("vigencia");

    verify(scheduleTemplateRepository, never()).save(any());
  }

  @Test
  void create_throws_whenOverlapsAnotherDoctorTemplate_regardlessOfDifferentStablishmentOrService() {
    // Establishment/service intentionally would differ from any prior
    // template — proves the doctor-scoped overlap rule ignores them: a
    // doctor cannot be double-templated into two places at the same weekly
    // instant.
    UUID doctorUuid = UUID.randomUUID();
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment()));
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));
    when(doctorRepository.findById(doctorUuid)).thenReturn(Optional.of(doctor(doctorUuid)));
    when(scheduleTemplateRepository.existsOverlappingForDoctor(
        eq(doctorUuid), eq(DayOfWeek.MONDAY), eq(LocalTime.of(8, 0)), eq(LocalTime.of(12, 0)),
        any(), any(), isNull())).thenReturn(true);

    assertThatThrownBy(() -> scheduleTemplateService.create(
        doctorDto(doctorUuid, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("superpone");

    verify(scheduleTemplateRepository, never()).save(any());
  }

  @Test
  void create_throws_whenOverlapsAnotherPoolTemplate_forSameStablishmentServiceAndDay() {
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment()));
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));
    when(scheduleTemplateRepository.existsOverlappingForPool(
        eq(2L), eq(1L), eq(DayOfWeek.MONDAY), eq(LocalTime.of(10, 0)), eq(LocalTime.of(14, 0)),
        any(), any(), isNull())).thenReturn(true);

    assertThatThrownBy(() -> scheduleTemplateService.create(
        poolDto(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(14, 0))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("superpone");

    verify(scheduleTemplateRepository, never()).save(any());
  }

  // -- getAll() -----------------------------------------------------------

  @SuppressWarnings("unchecked")
  private ArgumentCaptor<Specification<ScheduleTemplate>> captureSpecification() {
    when(scheduleTemplateRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
    return ArgumentCaptor.forClass(Specification.class);
  }

  @Test
  void getAll_withStablishmentServiceAndDoctorFilters_appliesEqualityOnEachOne() {
    Pageable pageable = PageRequest.of(0, 10);
    ArgumentCaptor<Specification<ScheduleTemplate>> captor = captureSpecification();
    UUID doctorUuid = UUID.randomUUID();

    scheduleTemplateService.getAll(2L, 1L, doctorUuid, pageable);

    verify(scheduleTemplateRepository).findAll(captor.capture(), any(Pageable.class));

    Root<ScheduleTemplate> root = mock(Root.class, RETURNS_DEEP_STUBS);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

    captor.getValue().toPredicate(root, query, cb);

    verify(cb).equal(root.get("stablishment").get("id"), 2L);
    verify(cb).equal(root.get("servicio").get("id"), 1L);
    verify(cb).equal(root.get("doctor").get("uuid"), doctorUuid);
  }

  @Test
  void getAll_withNoFilters_buildsNoPredicates() {
    Pageable pageable = PageRequest.of(0, 10);
    ArgumentCaptor<Specification<ScheduleTemplate>> captor = captureSpecification();

    scheduleTemplateService.getAll(pageable);

    verify(scheduleTemplateRepository).findAll(captor.capture(), any(Pageable.class));

    Root<ScheduleTemplate> root = mock(Root.class, RETURNS_DEEP_STUBS);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

    captor.getValue().toPredicate(root, query, cb);

    verify(cb, never()).equal(any(), any());
  }

  // -- getById() ----------------------------------------------------------

  @Test
  void getById_returnsMappedDTO_whenFound() {
    ScheduleTemplate entity = ScheduleTemplate.builder().id(7L).stablishment(stablishment()).servicio(servicio())
        .dayOfWeek(DayOfWeek.TUESDAY).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
        .slotIntervalMinutes(20).validFrom(LocalDate.of(2026, 1, 1)).build();
    when(scheduleTemplateRepository.findById(7L)).thenReturn(Optional.of(entity));

    ScheduleTemplateDTO result = scheduleTemplateService.getById(7L);

    assertThat(result.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
    assertThat(result.getSlotIntervalMinutes()).isEqualTo(20);
  }

  @Test
  void getById_throws_whenNotFound() {
    when(scheduleTemplateRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> scheduleTemplateService.getById(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");
  }

  // -- update() -------------------------------------------------------------

  @Test
  void update_changesFields_whenNoOverlapExcludingItself() {
    ScheduleTemplate existing = ScheduleTemplate.builder().id(5L).stablishment(stablishment()).servicio(servicio())
        .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
        .slotIntervalMinutes(30).validFrom(LocalDate.of(2026, 1, 1)).build();
    when(scheduleTemplateRepository.findById(5L)).thenReturn(Optional.of(existing));
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment()));
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));
    when(scheduleTemplateRepository.existsOverlappingForPool(
        eq(2L), eq(1L), eq(DayOfWeek.MONDAY), eq(LocalTime.of(8, 0)), eq(LocalTime.of(13, 0)),
        any(), any(), eq(5L))).thenReturn(false);
    when(scheduleTemplateRepository.save(any(ScheduleTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

    ScheduleTemplateDTO result = scheduleTemplateService.update(5L,
        poolDto(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(13, 0)));

    assertThat(result.getEndTime()).isEqualTo(LocalTime.of(13, 0));
  }

  @Test
  void update_throws_whenNotFound() {
    when(scheduleTemplateRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> scheduleTemplateService.update(404L,
        poolDto(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");
  }

  @Test
  void update_throws_whenOverlapsAnotherExistingTemplate() {
    ScheduleTemplate existing = ScheduleTemplate.builder().id(5L).stablishment(stablishment()).servicio(servicio())
        .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
        .slotIntervalMinutes(30).validFrom(LocalDate.of(2026, 1, 1)).build();
    when(scheduleTemplateRepository.findById(5L)).thenReturn(Optional.of(existing));
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(stablishment()));
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));
    when(scheduleTemplateRepository.existsOverlappingForPool(
        eq(2L), eq(1L), eq(DayOfWeek.MONDAY), any(), any(), any(), any(), eq(5L))).thenReturn(true);

    assertThatThrownBy(() -> scheduleTemplateService.update(5L,
        poolDto(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("superpone");

    verify(scheduleTemplateRepository, never()).save(any());
  }

  // -- delete() -------------------------------------------------------------

  @Test
  void delete_removesTemplate_whenExists() {
    when(scheduleTemplateRepository.existsById(1L)).thenReturn(true);

    scheduleTemplateService.delete(1L);

    verify(scheduleTemplateRepository).deleteById(1L);
  }

  @Test
  void delete_throws_whenNotFound() {
    when(scheduleTemplateRepository.existsById(404L)).thenReturn(false);

    assertThatThrownBy(() -> scheduleTemplateService.delete(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");

    verify(scheduleTemplateRepository, never()).deleteById(any());
  }
}
