package com.devluis.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.devluis.dto.InsurerDTO;
import com.devluis.entity.Insurer;
import com.devluis.repository.CoveragePlanRepository;
import com.devluis.repository.InsurerRepository;

import lombok.Data;

@Service
@Data
public class InsurerService {
  private final InsurerRepository insurerRepository;
  private final CoveragePlanRepository coveragePlanRepository;

  public InsurerDTO create(InsurerDTO dto) {
    Insurer saved = insurerRepository.save(mapToEntity(dto));
    return mapToDTO(saved);
  }

  public Page<InsurerDTO> getAll(String name, Pageable pageable) {
    if (name != null && !name.trim().isEmpty()) {
      return insurerRepository.findByNameContainingIgnoreCase(name.trim(), pageable).map(this::mapToDTO);
    }
    return insurerRepository.findAll(pageable).map(this::mapToDTO);
  }

  public Page<InsurerDTO> getAll(Pageable pageable) {
    return getAll(null, pageable);
  }

  public InsurerDTO getById(Long id) {
    Insurer insurer = insurerRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Aseguradora no encontrada"));
    return mapToDTO(insurer);
  }

  public InsurerDTO update(Long id, InsurerDTO dto) {
    Insurer insurer = insurerRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Aseguradora no encontrada"));

    insurer.setName(dto.getName());
    insurer.setType(dto.getType());

    Insurer updated = insurerRepository.save(insurer);
    return mapToDTO(updated);
  }

  public void delete(Long id) {
    if (!insurerRepository.existsById(id)) {
      throw new RuntimeException("Aseguradora no encontrada");
    }
    // Same "cannot delete because X still references it" idiom as
    // BlockReasonService.delete.
    if (coveragePlanRepository.existsByInsurerId(id)) {
      throw new RuntimeException(
          "No se puede eliminar la aseguradora porque tiene planes de cobertura asociados. Elimine o reasigne esos planes antes de eliminarla.");
    }
    insurerRepository.deleteById(id);
  }

  private Insurer mapToEntity(InsurerDTO dto) {
    return Insurer.builder()
        .id(dto.getId())
        .name(dto.getName())
        .type(dto.getType())
        .build();
  }

  private InsurerDTO mapToDTO(Insurer entity) {
    return InsurerDTO.builder()
        .id(entity.getId())
        .name(entity.getName())
        .type(entity.getType())
        .createdAt(entity.getCreatedAt())
        .build();
  }
}
