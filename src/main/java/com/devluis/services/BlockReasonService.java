package com.devluis.services;

import com.devluis.dto.BlockReasonDTO;
import com.devluis.entity.BlockReason;
import com.devluis.repository.BlockReasonRepository;
import com.devluis.repository.HolidayRepository;
import com.devluis.repository.TimeOffRepository;

import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Data
public class BlockReasonService {
  private final BlockReasonRepository blockReasonRepository;
  private final HolidayRepository holidayRepository;
  private final TimeOffRepository timeOffRepository;

  public BlockReasonDTO create(BlockReasonDTO dto) {
    BlockReason saved = blockReasonRepository.save(mapToEntity(dto));
    return mapToDTO(saved);
  }

  public Page<BlockReasonDTO> getAll(String description, Pageable pageable) {
    if (description != null && !description.trim().isEmpty()) {
      return blockReasonRepository.findByDescriptionContainingIgnoreCase(description.trim(), pageable)
          .map(this::mapToDTO);
    }
    return blockReasonRepository.findAll(pageable).map(this::mapToDTO);
  }

  public Page<BlockReasonDTO> getAll(Pageable pageable) {
    return getAll(null, pageable);
  }

  public BlockReasonDTO getById(Long id) {
    BlockReason reason = blockReasonRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Motivo no encontrado"));
    return mapToDTO(reason);
  }

  public BlockReasonDTO update(Long id, BlockReasonDTO dto) {
    BlockReason reason = blockReasonRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Motivo no encontrado"));

    reason.setDescription(dto.getDescription());

    BlockReason updated = blockReasonRepository.save(reason);
    return mapToDTO(updated);
  }

  public void delete(Long id) {
    if (!blockReasonRepository.existsById(id)) {
      throw new RuntimeException("Motivo no encontrado");
    }
    // A BlockReason is a shared catalog entry: removing it out from under a
    // Holiday/TimeOff that still points to it would leave a dangling FK (or
    // silently null it out depending on the DB), so both referencing tables
    // are checked first — same "cannot delete because X still references
    // it" idiom as StablishmentService.delete/ScheduleService.delete guard
    // against Turn.
    if (holidayRepository.existsByReasonId(id)) {
      throw new RuntimeException(
          "No se puede eliminar el motivo porque está asociado a uno o más feriados. Reasigne o elimine esos feriados antes de eliminarlo.");
    }
    if (timeOffRepository.existsByReasonId(id)) {
      throw new RuntimeException(
          "No se puede eliminar el motivo porque está asociado a una o más ausencias (vacaciones o permisos). Reasigne o elimine esas ausencias antes de eliminarlo.");
    }
    blockReasonRepository.deleteById(id);
  }

  private BlockReason mapToEntity(BlockReasonDTO dto) {
    return BlockReason.builder()
        .id(dto.getId())
        .description(dto.getDescription())
        .build();
  }

  private BlockReasonDTO mapToDTO(BlockReason entity) {
    return BlockReasonDTO.builder()
        .id(entity.getId())
        .description(entity.getDescription())
        .build();
  }
}
