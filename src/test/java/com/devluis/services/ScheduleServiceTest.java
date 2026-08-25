package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

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

import com.devluis.entity.Schedule;
import com.devluis.repository.DoctorRepository;
import com.devluis.repository.ScheduleRepository;
import com.devluis.repository.ServiceRepository;
import com.devluis.repository.StablishmentRepository;
import com.devluis.repository.TurnRepository;
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

  private ScheduleService scheduleService;

  @BeforeEach
  void setUp() {
    scheduleService = new ScheduleService(
        scheduleRepository, doctorRepository, serviceRepository, stablishmentRepository, turnRepository);
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
}
