package com.devluis.services;

import com.devluis.dto.BlockReasonDTO;
import com.devluis.entity.BlockReason;
import com.devluis.repository.BlockReasonRepository;
import com.devluis.types.BlockReasonKind;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Data
public class BlockReasonService {

  private final BlockReasonRepository blockReasonRepository;

  public BlockReasonDTO create(BlockReasonDTO dto) {
    String name = dto.getName().trim();
    assertNameIsFree(name, null);

    BlockReason reason = BlockReason.builder()
        .name(name)
        .kind(dto.getKind())
        .active(dto.getActive() == null || dto.getActive())
        .build();

    return mapToDTO(blockReasonRepository.save(reason));
  }

  public Page<BlockReasonDTO> getAll(Pageable pageable) {
    return blockReasonRepository.findAll(pageable).map(this::mapToDTO);
  }

  /**
   * Los motivos activos, opcionalmente de un solo tipo.
   *
   * `kind` en null devuelve todos: la pantalla de catálogo los quiere todos, y
   * las tres pantallas que bloquean agenda quieren solo los suyos.
   */
  public List<BlockReasonDTO> getActive(BlockReasonKind kind) {
    List<BlockReason> reasons = kind == null
        ? blockReasonRepository.findByActiveTrueOrderByNameAsc()
        : blockReasonRepository.findByKindAndActiveTrueOrderByNameAsc(kind);

    return reasons.stream().map(this::mapToDTO).collect(Collectors.toList());
  }

  public BlockReasonDTO getById(Long id) {
    return mapToDTO(findOrThrow(id));
  }

  public BlockReasonDTO update(Long id, BlockReasonDTO dto) {
    BlockReason reason = findOrThrow(id);
    String name = dto.getName().trim();

    if (!reason.getName().equalsIgnoreCase(name)) {
      assertNameIsFree(name, id);
    }

    reason.setName(name);
    reason.setKind(dto.getKind());
    if (dto.getActive() != null) {
      reason.setActive(dto.getActive());
    }

    return mapToDTO(blockReasonRepository.save(reason));
  }

  /** Lógico: hay filas de `time_off` apuntando acá y su motivo tiene que seguir legible. */
  public void delete(Long id) {
    BlockReason reason = findOrThrow(id);
    reason.setActive(false);
    blockReasonRepository.save(reason);
  }

  /** La entidad, para que `TimeOffService` pueda resolver un `reasonId`. */
  public BlockReason getEntity(Long id) {
    return findOrThrow(id);
  }

  private void assertNameIsFree(String name, Long ignoreId) {
    blockReasonRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
      if (ignoreId == null || !existing.getId().equals(ignoreId)) {
        throw new RuntimeException("Ya existe un motivo con ese nombre");
      }
    });
  }

  private BlockReason findOrThrow(Long id) {
    return blockReasonRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Motivo no encontrado"));
  }

  private BlockReasonDTO mapToDTO(BlockReason entity) {
    return BlockReasonDTO.builder()
        .id(entity.getId())
        .name(entity.getName())
        .kind(entity.getKind())
        .active(entity.getActive())
        .createdAt(entity.getCreatedAt())
        .build();
  }
}
