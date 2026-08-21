package com.devluis.services;

import com.devluis.dto.StablishmentDTO;
import com.devluis.entity.Stablishment;
import com.devluis.repository.StablishmentRepository;

import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Data
public class StablishmentService {
  private final StablishmentRepository stablishmentRepository;

  private final com.devluis.repository.ServiceRepository serviceRepository;

  public StablishmentDTO create(StablishmentDTO dto) {
    Stablishment stablishment = mapToEntity(dto);
    Stablishment saved = stablishmentRepository.save(stablishment);
    return mapToDTO(saved);
  }

  public Page<StablishmentDTO> getAll(
      Pageable pageable) {
    return stablishmentRepository.findAll(pageable)
        .map(this::mapToDTO);
  }

  public StablishmentDTO getById(Long id) {
    Stablishment stablishment = stablishmentRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));
    return mapToDTO(stablishment);
  }

  public StablishmentDTO update(Long id, StablishmentDTO dto) {
    Stablishment stablishment = stablishmentRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));

    stablishment.setName(dto.getName());
    stablishment.setAddress(dto.getAddress());

    Stablishment updated = stablishmentRepository.save(stablishment);
    return mapToDTO(updated);
  }

  public void delete(Long id) {
    if (!stablishmentRepository.existsById(id)) {
      throw new RuntimeException("Establecimiento no encontrado");
    }
    stablishmentRepository.deleteById(id);
  }

  public StablishmentDTO assignService(Long stablishmentId, Long serviceId) {
    Stablishment stablishment = stablishmentRepository.findById(stablishmentId)
        .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));

    com.devluis.entity.Servicio service = serviceRepository.findById(serviceId)
        .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));

    if (stablishment.getServices() == null) {
      stablishment.setServices(new java.util.ArrayList<>());
    }
    
    if (!stablishment.getServices().contains(service)) {
      stablishment.getServices().add(service);
      stablishmentRepository.save(stablishment);
    }
    
    return mapToDTO(stablishment);
  }

  private Stablishment mapToEntity(StablishmentDTO dto) {
    return Stablishment.builder()
        .id(dto.getId())
        .name(dto.getName())
        .address(dto.getAddress())
        .build();
  }

  private StablishmentDTO mapToDTO(Stablishment entity) {
    java.util.List<com.devluis.dto.ServicioDTO> servicioDTOs = null;
    if (entity.getServices() != null) {
      servicioDTOs = entity.getServices().stream().map(s -> 
          com.devluis.dto.ServicioDTO.builder()
              .id(s.getId())
              .name(s.getName())
              .price(s.getPrice())
              .build()
      ).toList();
    }

    return StablishmentDTO.builder()
        .id(entity.getId())
        .name(entity.getName())
        .address(entity.getAddress())
        .services(servicioDTOs)
        .build();
  }
}
