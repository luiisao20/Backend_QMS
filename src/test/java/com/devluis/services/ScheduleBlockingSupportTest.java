package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.devluis.entity.Schedule;
import com.devluis.repository.ScheduleRepository;
import com.devluis.types.ScheduleStatus;

/**
 * Shared sweep used by HolidayService/TimeOffService when a Holiday or
 * TimeOff is created: it must free-slot-block without ever destroying a
 * booked turn. Extracted out of both services so this safety rule has one
 * focused test suite instead of being re-verified through two heavier
 * service-level mock setups.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleBlockingSupportTest {

  @Mock
  private ScheduleRepository scheduleRepository;

  private ScheduleBlockingSupport support;

  @BeforeEach
  void setUp() {
    support = new ScheduleBlockingSupport(scheduleRepository);
  }

  @Test
  void blocksFreeSchedules_toUnavailable_andReportsNoConflicts() {
    Schedule free = Schedule.builder().id(1L).status(ScheduleStatus.STATUS_FREE).build();

    List<Long> conflicts = support.blockFreeSchedulesAndReportConflicts(List.of(free));

    assertThat(free.getStatus()).isEqualTo(ScheduleStatus.STATUS_UNAVAILABLE);
    verify(scheduleRepository).saveAndFlush(free);
    assertThat(conflicts).isEmpty();
  }

  @Test
  void reportsOccupiedSchedulesAsConflicts_withoutTouchingThem() {
    Schedule occupied = Schedule.builder().id(2L).status(ScheduleStatus.STATUS_OCCUPIED).build();

    List<Long> conflicts = support.blockFreeSchedulesAndReportConflicts(List.of(occupied));

    assertThat(occupied.getStatus()).isEqualTo(ScheduleStatus.STATUS_OCCUPIED);
    verify(scheduleRepository, never()).saveAndFlush(any());
    assertThat(conflicts).containsExactly(2L);
  }

  @Test
  void leavesAlreadyUnavailableSchedules_untouched_andDoesNotReportThemAsConflicts() {
    Schedule alreadyBlocked = Schedule.builder().id(3L).status(ScheduleStatus.STATUS_UNAVAILABLE).build();

    List<Long> conflicts = support.blockFreeSchedulesAndReportConflicts(List.of(alreadyBlocked));

    verify(scheduleRepository, never()).saveAndFlush(any());
    assertThat(conflicts).isEmpty();
  }

  @Test
  void treatsALostOptimisticLockRace_asAConflict_insteadOfFailingTheWholeSweep() {
    // A booking can flip STATUS_FREE -> STATUS_OCCUPIED between our read and
    // our saveAndFlush; @Version on Schedule turns that race into
    // ObjectOptimisticLockingFailureException here (mirrors
    // TurnService.occupySchedule). This must degrade to "report as
    // conflict", not blow up the whole Holiday/TimeOff creation.
    Schedule justBooked = Schedule.builder().id(4L).status(ScheduleStatus.STATUS_FREE).build();
    when(scheduleRepository.saveAndFlush(justBooked)).thenThrow(new ObjectOptimisticLockingFailureException(Schedule.class, 4L));

    List<Long> conflicts = support.blockFreeSchedulesAndReportConflicts(List.of(justBooked));

    assertThat(conflicts).containsExactly(4L);
  }

  @Test
  void processesMultipleSchedules_mixingFreeAndOccupied() {
    Schedule free = Schedule.builder().id(5L).status(ScheduleStatus.STATUS_FREE).build();
    Schedule occupied = Schedule.builder().id(6L).status(ScheduleStatus.STATUS_OCCUPIED).build();

    List<Long> conflicts = support.blockFreeSchedulesAndReportConflicts(List.of(free, occupied));

    assertThat(free.getStatus()).isEqualTo(ScheduleStatus.STATUS_UNAVAILABLE);
    assertThat(conflicts).containsExactly(6L);
  }
}
