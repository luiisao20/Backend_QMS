package com.devluis.services;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devluis.dto.BlockReasonDTO;
import com.devluis.dto.HolidayDTO;
import com.devluis.dto.StablishmentDTO;
import com.devluis.entity.BlockReason;
import com.devluis.entity.Holiday;
import com.devluis.entity.Schedule;
import com.devluis.entity.Stablishment;
import com.devluis.repository.BlockReasonRepository;
import com.devluis.repository.HolidayRepository;
import com.devluis.repository.ScheduleRepository;
import com.devluis.repository.StablishmentRepository;

import lombok.Data;

@Service
@Data
public class HolidayService {
  private final HolidayRepository holidayRepository;
  private final BlockReasonRepository blockReasonRepository;
  private final StablishmentRepository stablishmentRepository;
  private final ScheduleRepository scheduleRepository;
  private final ScheduleBlockingSupport scheduleBlockingSupport;

  @Transactional
  public HolidayDTO create(HolidayDTO dto) {
    Stablishment stablishment = resolveStablishment(dto);
    BlockReason reason = resolveReason(dto);

    Holiday holiday = Holiday.builder()
        .date(dto.getDate())
        .description(dto.getDescription())
        .stablishment(stablishment)
        .reason(reason)
        .build();

    Holiday saved = holidayRepository.save(holiday);
    List<Long> conflicts = sweepAffectedSchedules(saved.getDate(), stablishment);
    return mapToDTO(saved, conflicts);
  }

  public Page<HolidayDTO> getAll(Long stablishmentId, Pageable pageable) {
    if (stablishmentId != null) {
      return holidayRepository.findByStablishmentId(stablishmentId, pageable).map(h -> mapToDTO(h, null));
    }
    return holidayRepository.findAll(pageable).map(h -> mapToDTO(h, null));
  }

  public Page<HolidayDTO> getAll(Pageable pageable) {
    return getAll(null, pageable);
  }

  public HolidayDTO getById(Long id) {
    Holiday holiday = holidayRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Feriado no encontrado"));
    return mapToDTO(holiday, null);
  }

  @Transactional
  public HolidayDTO update(Long id, HolidayDTO dto) {
    Holiday holiday = holidayRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Feriado no encontrado"));

    Stablishment stablishment = resolveStablishment(dto);
    BlockReason reason = resolveReason(dto);

    holiday.setDate(dto.getDate());
    holiday.setDescription(dto.getDescription());
    holiday.setStablishment(stablishment);
    holiday.setReason(reason);

    Holiday updated = holidayRepository.save(holiday);
    // Re-running the sweep on update covers the common "the date/scope
    // changed" case. It does NOT revert schedules that were previously
    // blocked by the old date/scope and are now out of range back to
    // STATUS_FREE — see the apply report for why that direction is not
    // automated (no stored link from Schedule back to the Holiday that
    // blocked it, so "was it blocked because of THIS holiday, or for an
    // unrelated reason?" cannot be answered safely).
    List<Long> conflicts = sweepAffectedSchedules(updated.getDate(), stablishment);
    return mapToDTO(updated, conflicts);
  }

  public void delete(Long id) {
    if (!holidayRepository.existsById(id)) {
      throw new RuntimeException("Feriado no encontrado");
    }
    // Deliberately NOT reverting any schedule this holiday previously marked
    // STATUS_UNAVAILABLE back to STATUS_FREE — same reasoning as update()
    // above. A human reviews and frees the slot explicitly if appropriate.
    holidayRepository.deleteById(id);
  }

  private Stablishment resolveStablishment(HolidayDTO dto) {
    if (dto.getStablishment() == null || dto.getStablishment().getId() == null) {
      return null;
    }
    return stablishmentRepository.findById(dto.getStablishment().getId())
        .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));
  }

  private BlockReason resolveReason(HolidayDTO dto) {
    return blockReasonRepository.findById(dto.getReason().getId())
        .orElseThrow(() -> new RuntimeException("Motivo no encontrado"));
  }

  private List<Long> sweepAffectedSchedules(LocalDate date, Stablishment stablishment) {
    List<Schedule> affected = stablishment == null
        ? scheduleRepository.findByDate(date)
        : scheduleRepository.findByDateAndStablishmentId(date, stablishment.getId());
    return scheduleBlockingSupport.blockFreeSchedulesAndReportConflicts(affected);
  }

  private HolidayDTO mapToDTO(Holiday entity, List<Long> conflictingScheduleIds) {
    StablishmentDTO stablishmentDTO = null;
    if (entity.getStablishment() != null) {
      stablishmentDTO = StablishmentDTO.builder()
          .id(entity.getStablishment().getId())
          .name(entity.getStablishment().getName())
          .address(entity.getStablishment().getAddress())
          .build();
    }

    BlockReasonDTO reasonDTO = null;
    if (entity.getReason() != null) {
      reasonDTO = BlockReasonDTO.builder()
          .id(entity.getReason().getId())
          .description(entity.getReason().getDescription())
          .build();
    }

    return HolidayDTO.builder()
        .id(entity.getId())
        .date(entity.getDate())
        .description(entity.getDescription())
        .stablishment(stablishmentDTO)
        .reason(reasonDTO)
        .createdAt(entity.getCreatedAt())
        .conflictingScheduleIds(conflictingScheduleIds)
        .build();
  }
}
