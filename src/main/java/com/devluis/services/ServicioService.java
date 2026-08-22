package com.devluis.services;

import com.devluis.dto.ServicioDTO;
import com.devluis.entity.Servicio;
import com.devluis.repository.ServiceRepository;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Data
public class ServicioService {

  private final ServiceRepository serviceRepository;

  public ServicioDTO create(ServicioDTO dto) {
    Servicio servicio = mapToEntity(dto);
    Servicio saved = serviceRepository.save(servicio);
    return mapToDTO(saved);
  }

  public List<ServicioDTO> getMyServices(UUID doctorId) {
    List<Servicio> services = serviceRepository.findServicesByDoctorId(doctorId);
    return services.stream().map(this::mapToDTO).collect(Collectors.toList());
  }

  public Page<ServicioDTO> getAll(Pageable pageable) {
    return serviceRepository.findAll(pageable)
        .map(this::mapToDTO);
  }

  public ServicioDTO getById(Long id) {
    Servicio servicio = serviceRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));
    return mapToDTO(servicio);
  }

  public ServicioDTO update(Long id, ServicioDTO dto) {
    Servicio servicio = serviceRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));

    servicio.setName(dto.getName());
    servicio.setPrice(dto.getPrice());
    servicio.setDiscount(dto.getDiscount());

    Servicio updated = serviceRepository.save(servicio);
    return mapToDTO(updated);
  }

  public void delete(Long id) {
    if (!serviceRepository.existsById(id)) {
      throw new RuntimeException("Servicio no encontrado");
    }
    serviceRepository.deleteById(id);
  }

  private Servicio mapToEntity(ServicioDTO dto) {
    return Servicio.builder()
        .id(dto.getId())
        .name(dto.getName())
        .price(dto.getPrice())
        .discount(dto.getDiscount())
        .build();
  }

  private ServicioDTO mapToDTO(Servicio entity) {
    return ServicioDTO.builder()
        .id(entity.getId())
        .name(entity.getName())
        .price(entity.getPrice())
        .discount(entity.getDiscount())
        .build();
  }
}
