package com.devluis.services;

import com.devluis.dto.BlockReasonDTO;
import com.devluis.dto.DoctorDTO;
import com.devluis.dto.TimeOffDTO;
import com.devluis.entity.BlockReason;
import com.devluis.entity.Doctor;
import com.devluis.entity.TimeOff;
import com.devluis.repository.BlockReasonRepository;
import com.devluis.repository.DoctorRepository;
import com.devluis.repository.TimeOffRepository;
import com.devluis.types.TimeOffKind;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Data
public class TimeOffService {

  private final TimeOffRepository timeOffRepository;
  private final DoctorRepository doctorRepository;
  private final BlockReasonRepository blockReasonRepository;

  public TimeOffDTO create(TimeOffDTO dto) {
    Doctor doctor = resolveDoctor(dto);
    assertValidRange(dto.getStartDate(), dto.getEndDate());
    assertNoOverlap(doctor.getUuid(), dto.getStartDate(), dto.getEndDate(), null);

    TimeOff timeOff = TimeOff.builder()
        .doctor(doctor)
        .kind(dto.getKind())
        .startDate(dto.getStartDate())
        .endDate(dto.getEndDate())
        .reason(resolveReason(dto))
        .notes(dto.getNotes())
        .build();

    return mapToDTO(timeOffRepository.save(timeOff));
  }

  /** Todo filtro es opcional. `kind` es lo que separa Vacaciones de Permisos. */
  public Page<TimeOffDTO> search(UUID doctorId, TimeOffKind kind, LocalDate from, LocalDate to,
      Pageable pageable) {
    return timeOffRepository.search(doctorId, kind, from, to, pageable).map(this::mapToDTO);
  }

  public TimeOffDTO getById(Long id) {
    return mapToDTO(findOrThrow(id));
  }

  public TimeOffDTO update(Long id, TimeOffDTO dto) {
    TimeOff timeOff = findOrThrow(id);
    Doctor doctor = resolveDoctor(dto);
    assertValidRange(dto.getStartDate(), dto.getEndDate());
    assertNoOverlap(doctor.getUuid(), dto.getStartDate(), dto.getEndDate(), id);

    timeOff.setDoctor(doctor);
    timeOff.setKind(dto.getKind());
    timeOff.setStartDate(dto.getStartDate());
    timeOff.setEndDate(dto.getEndDate());
    timeOff.setReason(resolveReason(dto));
    timeOff.setNotes(dto.getNotes());

    return mapToDTO(timeOffRepository.save(timeOff));
  }

  /**
   * Físico. Una ausencia cancelada no es un dato histórico que otra fila
   * referencie: es un bloqueo que dejó de aplicar, y dejarlo desactivado en la
   * tabla obligaría a cada consulta de disponibilidad a filtrarlo.
   */
  public void delete(Long id) {
    if (!timeOffRepository.existsById(id)) {
      throw new RuntimeException("Ausencia no encontrada");
    }
    timeOffRepository.deleteById(id);
  }

  private Doctor resolveDoctor(TimeOffDTO dto) {
    if (dto.getDoctor() == null || dto.getDoctor().getUuid() == null) {
      throw new RuntimeException("El doctor es obligatorio");
    }
    return doctorRepository.findById(dto.getDoctor().getUuid())
        .orElseThrow(() -> new RuntimeException("Doctor no encontrado"));
  }

  private BlockReason resolveReason(TimeOffDTO dto) {
    if (dto.getReason() == null || dto.getReason().getId() == null) {
      return null; // El catálogo de motivos puede estar vacío todavía.
    }
    return blockReasonRepository.findById(dto.getReason().getId())
        .orElseThrow(() -> new RuntimeException("Motivo no encontrado"));
  }

  /** `endDate` es inclusivo, así que una ausencia de un día tiene las dos fechas iguales. */
  private void assertValidRange(LocalDate start, LocalDate end) {
    if (end.isBefore(start)) {
      throw new RuntimeException("La fecha de fin no puede ser anterior a la de inicio");
    }
  }

  /**
   * Dos ausencias del mismo doctor que se pisan son un error de carga, no un
   * caso de uso: la agenda no sabría cuál motivo mostrar para el día en común, y
   * borrar una deja el bloqueo puesto por la otra.
   */
  private void assertNoOverlap(UUID doctorId, LocalDate start, LocalDate end, Long ignoreId) {
    List<TimeOff> overlapping = timeOffRepository.findOverlapping(doctorId, start, end, ignoreId);
    if (!overlapping.isEmpty()) {
      throw new RuntimeException("El doctor ya tiene una ausencia registrada en ese rango de fechas");
    }
  }

  private TimeOff findOrThrow(Long id) {
    return timeOffRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Ausencia no encontrada"));
  }

  private TimeOffDTO mapToDTO(TimeOff entity) {
    DoctorDTO doctorDTO = null;
    if (entity.getDoctor() != null) {
      doctorDTO = DoctorDTO.builder()
          .uuid(entity.getDoctor().getUuid())
          .email(entity.getDoctor().getEmail())
          .firstName(entity.getDoctor().getFirstName())
          .lastName(entity.getDoctor().getLastName())
          .speciality(entity.getDoctor().getSpeciality())
          .ci(entity.getDoctor().getCi())
          .build();
    }

    BlockReasonDTO reasonDTO = null;
    if (entity.getReason() != null) {
      reasonDTO = BlockReasonDTO.builder()
          .id(entity.getReason().getId())
          .name(entity.getReason().getName())
          .kind(entity.getReason().getKind())
          .active(entity.getReason().getActive())
          .build();
    }

    return TimeOffDTO.builder()
        .id(entity.getId())
        .doctor(doctorDTO)
        .kind(entity.getKind())
        .startDate(entity.getStartDate())
        .endDate(entity.getEndDate())
        .reason(reasonDTO)
        .notes(entity.getNotes())
        .createdAt(entity.getCreatedAt())
        .build();
  }
}
