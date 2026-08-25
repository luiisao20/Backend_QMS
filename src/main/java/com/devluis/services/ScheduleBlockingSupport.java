package com.devluis.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.devluis.entity.Schedule;
import com.devluis.repository.ScheduleRepository;
import com.devluis.types.ScheduleStatus;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared side effect for HolidayService/TimeOffService: when a Holiday or
 * TimeOff is created (or its date range widened on update), every Schedule
 * slot it now covers must stop being offered to patients, WITHOUT ever
 * destroying a booked turn. Extracted into its own class so this rule has a
 * single, focused test suite instead of being re-verified through two
 * heavier service-level mock setups (Extract-Before-Mock).
 *
 * Decision (see apply report for the full writeup): a schedule with no turn
 * (STATUS_FREE) is safe to flip to STATUS_UNAVAILABLE automatically. A
 * schedule already claimed by a turn (STATUS_OCCUPIED) is NEVER touched here
 * — its id is returned as a "conflict" for a human to resolve (cancel or
 * reassign the turn manually). This is the same cascade-safety rule as
 * Schedule/Stablishment delete: a Turn is never disposable as a side effect
 * of something else.
 */
@Service
@Data
@Slf4j
public class ScheduleBlockingSupport {

  private final ScheduleRepository scheduleRepository;

  public List<Long> blockFreeSchedulesAndReportConflicts(List<Schedule> schedules) {
    List<Long> conflictingScheduleIds = new ArrayList<>();

    for (Schedule schedule : schedules) {
      if (schedule.getStatus() == ScheduleStatus.STATUS_OCCUPIED) {
        conflictingScheduleIds.add(schedule.getId());
        continue;
      }

      if (schedule.getStatus() != ScheduleStatus.STATUS_FREE) {
        // Already STATUS_UNAVAILABLE (blocked by this or another rule) —
        // nothing to do, and not a new conflict.
        continue;
      }

      schedule.setStatus(ScheduleStatus.STATUS_UNAVAILABLE);
      try {
        // saveAndFlush (not save): Schedule carries @Version, so the UPDATE
        // must run now, synchronously, to catch a booking that wins the race
        // between our read and this write — see TurnService.occupySchedule
        // for the identical reasoning.
        scheduleRepository.saveAndFlush(schedule);
      } catch (ObjectOptimisticLockingFailureException e) {
        // Somebody booked this exact schedule in the same instant. Treat it
        // like an already-occupied conflict instead of failing the whole
        // Holiday/TimeOff creation — best-effort, same spirit as
        // TurnService.releaseScheduleIfUnclaimed.
        log.warn("No se pudo bloquear el horario {} porque fue reservado en el mismo instante: {}",
            schedule.getId(), e.getMessage());
        conflictingScheduleIds.add(schedule.getId());
      }
    }

    return conflictingScheduleIds;
  }
}
