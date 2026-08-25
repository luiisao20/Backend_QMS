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
  private final com.devluis.repository.DoctorRepository doctorRepository;
  private final com.devluis.repository.OperatorRepository operatorRepository;
  private final com.devluis.repository.TurnRepository turnRepository;

  public StablishmentDTO create(StablishmentDTO dto) {
    Stablishment stablishment = mapToEntity(dto);
    Stablishment saved = stablishmentRepository.save(stablishment);
    return mapToDTO(saved);
  }

  public Page<StablishmentDTO> getAll(String name, Pageable pageable) {
    if (name != null && !name.trim().isEmpty()) {
      return stablishmentRepository.findByNameContainingIgnoreCase(name.trim(), pageable)
          .map(this::mapToDTO);
    }
    return stablishmentRepository.findAll(pageable)
        .map(this::mapToDTO);
  }

  public Page<StablishmentDTO> getAll(Pageable pageable) {
    return getAll(null, pageable);
  }

  public StablishmentDTO getById(Long id) {
    Stablishment stablishment = stablishmentRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));
    return mapToDTO(stablishment);
  }

  public Page<com.devluis.dto.ServicioDTO> getServicesByStablishment(Long stablishmentId, String name, Pageable pageable) {
    if (!stablishmentRepository.existsById(stablishmentId)) {
      throw new RuntimeException("Establecimiento no encontrado");
    }
    String cleanName = (name != null && !name.trim().isEmpty()) ? name.trim() : null;
    return serviceRepository.findByStablishmentIdAndName(stablishmentId, cleanName, pageable)
        .map(s -> com.devluis.dto.ServicioDTO.builder()
            .id(s.getId())
            .name(s.getName())
            .price(s.getPrice())
            .discount(s.getDiscount())
            .build());
  }

  public Page<com.devluis.dto.DoctorDTO> getDoctorsByStablishment(Long stablishmentId, String name, String ci, Pageable pageable) {
    if (!stablishmentRepository.existsById(stablishmentId)) {
      throw new RuntimeException("Establecimiento no encontrado");
    }
    String cleanName = (name != null && !name.trim().isEmpty()) ? name.trim() : null;
    String cleanCi = (ci != null && !ci.trim().isEmpty()) ? ci.trim() : null;
    return doctorRepository.findByStablishmentIdAndFilters(stablishmentId, cleanName, cleanCi, pageable)
        .map(d -> com.devluis.dto.DoctorDTO.builder()
            .uuid(d.getUuid())
            .email(d.getEmail())
            .firstName(d.getFirstName())
            .lastName(d.getLastName())
            .speciality(d.getSpeciality())
            .gender(d.getGender())
            .ci(d.getCi())
            .build());
  }

  public Page<com.devluis.dto.OperatorDTO> getOperatorsByStablishment(Long stablishmentId, String name, Pageable pageable) {
    if (!stablishmentRepository.existsById(stablishmentId)) {
      throw new RuntimeException("Establecimiento no encontrado");
    }
    String cleanName = (name != null && !name.trim().isEmpty()) ? name.trim() : null;
    return operatorRepository.findByStablishmentIdAndName(stablishmentId, cleanName, pageable)
        .map(o -> com.devluis.dto.OperatorDTO.builder()
            .uuid(o.getUuid())
            .email(o.getEmail())
            .firstName(o.getFirstName())
            .lastName(o.getLastName())
            .role(o.getRole())
            .build());
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
    // A Schedule with no turns is disposable and gets cascade-removed with the
    // establishment (see Stablishment.schedules); a Turn never is. Block the
    // whole delete if any of its schedules still has booked turns instead of
    // letting a DataIntegrityViolationException surface from the DB.
    if (turnRepository.existsByScheduleStablishmentId(id)) {
      throw new RuntimeException(
          "No se puede eliminar el establecimiento porque tiene turnos reservados asociados a sus horarios. Cancele o reasigne los turnos antes de eliminarlo.");
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
    return StablishmentDTO.builder()
        .id(entity.getId())
        .name(entity.getName())
        .address(entity.getAddress())
        .build();
  }
}
