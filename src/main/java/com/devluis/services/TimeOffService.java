package com.devluis.services;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devluis.dto.BlockReasonDTO;
import com.devluis.dto.DoctorDTO;
import com.devluis.dto.TimeOffDTO;
import com.devluis.entity.BlockReason;
import com.devluis.entity.Doctor;
import com.devluis.entity.Schedule;
import com.devluis.entity.TimeOff;
import com.devluis.repository.BlockReasonRepository;
import com.devluis.repository.DoctorRepository;
import com.devluis.repository.ScheduleRepository;
import com.devluis.repository.TimeOffRepository;
import com.devluis.types.TimeOffKind;

import lombok.Data;

@Service
@Data
public class TimeOffService {
  private final TimeOffRepository timeOffRepository;
  private final DoctorRepository doctorRepository;
  private final BlockReasonRepository blockReasonRepository;
  private final ScheduleRepository scheduleRepository;
  private final ScheduleBlockingSupport scheduleBlockingSupport;

  @Transactional
  public TimeOffDTO create(TimeOffDTO dto) {
    Doctor doctor = resolveDoctor(dto);
    validateDateRange(dto);
    BlockReason reason = resolveReason(dto);

    TimeOff timeOff = TimeOff.builder()
        .doctor(doctor)
        .kind(dto.getKind())
        .startDate(dto.getStartDate())
        .endDate(dto.getEndDate())
        .reason(reason)
        .build();

    TimeOff saved = timeOffRepository.save(timeOff);
    List<Long> conflicts = sweepAffectedSchedules(doctor.getUuid(), saved.getStartDate(), saved.getEndDate());
    return mapToDTO(saved, conflicts);
  }

  public Page<TimeOffDTO> getAll(UUID doctorUuid, TimeOffKind kind, Pageable pageable) {
    if (doctorUuid != null && kind != null) {
      return timeOffRepository.findByDoctorUuidAndKind(doctorUuid, kind, pageable).map(t -> mapToDTO(t, null));
    }
    if (doctorUuid != null) {
      return timeOffRepository.findByDoctorUuid(doctorUuid, pageable).map(t -> mapToDTO(t, null));
    }
    if (kind != null) {
      return timeOffRepository.findByKind(kind, pageable).map(t -> mapToDTO(t, null));
    }
    return timeOffRepository.findAll(pageable).map(t -> mapToDTO(t, null));
  }

  public Page<TimeOffDTO> getAll(Pageable pageable) {
    return getAll(null, null, pageable);
  }

  public TimeOffDTO getById(Long id) {
    TimeOff timeOff = timeOffRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Ausencia no encontrada"));
    return mapToDTO(timeOff, null);
  }

  @Transactional
  public TimeOffDTO update(Long id, TimeOffDTO dto) {
    TimeOff timeOff = timeOffRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Ausencia no encontrada"));

    Doctor doctor = resolveDoctor(dto);
    validateDateRange(dto);
    BlockReason reason = resolveReason(dto);

    timeOff.setDoctor(doctor);
    timeOff.setKind(dto.getKind());
    timeOff.setStartDate(dto.getStartDate());
    timeOff.setEndDate(dto.getEndDate());
    timeOff.setReason(reason);

    TimeOff updated = timeOffRepository.save(timeOff);
    // Same non-goal as HolidayService#update: does not revert schedules a
    // shrunk/moved range no longer covers.
    List<Long> conflicts = sweepAffectedSchedules(doctor.getUuid(), updated.getStartDate(), updated.getEndDate());
    return mapToDTO(updated, conflicts);
  }

  public void delete(Long id) {
    if (!timeOffRepository.existsById(id)) {
      throw new RuntimeException("Ausencia no encontrada");
    }
    // Same non-goal as HolidayService#delete: previously blocked schedules
    // are not auto-reverted to STATUS_FREE.
    timeOffRepository.deleteById(id);
  }

  private Doctor resolveDoctor(TimeOffDTO dto) {
    return doctorRepository.findById(dto.getDoctor().getUuid())
        .orElseThrow(() -> new RuntimeException("Doctor no encontrado"));
  }

  private BlockReason resolveReason(TimeOffDTO dto) {
    return blockReasonRepository.findById(dto.getReason().getId())
        .orElseThrow(() -> new RuntimeException("Motivo no encontrado"));
  }

  private void validateDateRange(TimeOffDTO dto) {
    if (dto.getEndDate().isBefore(dto.getStartDate())) {
      throw new RuntimeException("La fecha de fin no puede ser anterior a la fecha de inicio");
    }
  }

  private List<Long> sweepAffectedSchedules(UUID doctorUuid, LocalDate startDate, LocalDate endDate) {
    List<Schedule> affected = scheduleRepository.findByDoctorUuidAndDateBetween(doctorUuid, startDate, endDate);
    return scheduleBlockingSupport.blockFreeSchedulesAndReportConflicts(affected);
  }

  private TimeOffDTO mapToDTO(TimeOff entity, List<Long> conflictingScheduleIds) {
    DoctorDTO doctorDTO = null;
    if (entity.getDoctor() != null) {
      doctorDTO = DoctorDTO.builder()
          .uuid(entity.getDoctor().getUuid())
          .firstName(entity.getDoctor().getFirstName())
          .lastName(entity.getDoctor().getLastName())
          .build();
    }

    BlockReasonDTO reasonDTO = null;
    if (entity.getReason() != null) {
      reasonDTO = BlockReasonDTO.builder()
          .id(entity.getReason().getId())
          .description(entity.getReason().getDescription())
          .build();
    }

    return TimeOffDTO.builder()
        .id(entity.getId())
        .doctor(doctorDTO)
        .kind(entity.getKind())
        .startDate(entity.getStartDate())
        .endDate(entity.getEndDate())
        .reason(reasonDTO)
        .createdAt(entity.getCreatedAt())
        .conflictingScheduleIds(conflictingScheduleIds)
        .build();
  }
}
