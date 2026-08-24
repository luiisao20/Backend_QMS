package com.devluis.services;

import com.devluis.dto.DoctorDTO;
import com.devluis.dto.ScheduleDTO;
import com.devluis.dto.ServicioDTO;
import com.devluis.dto.StablishmentDTO;
import com.devluis.entity.Doctor;
import com.devluis.entity.Schedule;
import com.devluis.entity.Servicio;
import com.devluis.repository.DoctorRepository;
import com.devluis.repository.ScheduleRepository;
import com.devluis.repository.ServiceRepository;
import com.devluis.types.ScheduleStatus;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Data
public class ServicioService {

  private final ServiceRepository serviceRepository;
  private final DoctorRepository doctorRepository;
  private final ScheduleRepository scheduleRepository;

  public ServicioDTO create(ServicioDTO dto) {
    Servicio servicio = mapToEntity(dto);
    Servicio saved = serviceRepository.save(servicio);
    return mapToDTO(saved);
  }

  public List<ServicioDTO> getMyServices(UUID doctorId) {
    List<Servicio> services = serviceRepository.findServicesByDoctorId(doctorId);
    return services.stream().map(this::mapToDTO).collect(Collectors.toList());
  }

  public Page<ServicioDTO> getAll(String name, Pageable pageable) {
    if (name != null && !name.trim().isEmpty()) {
      return serviceRepository.findByNameContainingIgnoreCase(name.trim(), pageable)
          .map(this::mapToDTO);
    }
    return serviceRepository.findAll(pageable)
        .map(this::mapToDTO);
  }

  public Page<ServicioDTO> getAll(Pageable pageable) {
    return getAll(null, pageable);
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

  public Page<DoctorDTO> getDoctorsByService(Long serviceId, String name, Pageable pageable) {
    if (!serviceRepository.existsById(serviceId)) {
      throw new RuntimeException("Servicio no encontrado");
    }
    String cleanName = (name != null && !name.trim().isEmpty()) ? name.trim() : null;
    return doctorRepository.findByServiceIdAndName(serviceId, cleanName, pageable)
        .map(this::mapDoctorToDTO);
  }

  public Page<ScheduleDTO> getSchedulesByService(Long serviceId, Long stablishmentId, LocalDate date, ScheduleStatus status, Pageable pageable) {
    if (!serviceRepository.existsById(serviceId)) {
      throw new RuntimeException("Servicio no encontrado");
    }

    if (pageable.getSort().isUnsorted()) {
      pageable = org.springframework.data.domain.PageRequest.of(
          pageable.getPageNumber(),
          pageable.getPageSize(),
          org.springframework.data.domain.Sort.by(
              org.springframework.data.domain.Sort.Order.asc("date"),
              org.springframework.data.domain.Sort.Order.asc("hour")
          )
      );
    }

    org.springframework.data.jpa.domain.Specification<Schedule> spec = (root, query, cb) -> {
      java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();

      predicates.add(cb.equal(root.get("service").get("id"), serviceId));

      if (stablishmentId != null) {
        predicates.add(cb.equal(root.get("stablishment").get("id"), stablishmentId));
      }

      if (date != null) {
        predicates.add(cb.equal(root.get("date"), date));
      }

      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }

      return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    };

    return scheduleRepository.findAll(spec, pageable).map(this::mapScheduleToDTO);
  }

  public void delete(Long id) {
    if (!serviceRepository.existsById(id)) {
      throw new RuntimeException("Servicio no encontrado");
    }
    serviceRepository.deleteById(id);
  }

  private DoctorDTO mapDoctorToDTO(Doctor doctor) {
    return DoctorDTO.builder()
        .uuid(doctor.getUuid())
        .email(doctor.getEmail())
        .firstName(doctor.getFirstName())
        .lastName(doctor.getLastName())
        .speciality(doctor.getSpeciality())
        .gender(doctor.getGender())
        .ci(doctor.getCi())
        .build();
  }

  private ScheduleDTO mapScheduleToDTO(Schedule entity) {
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

    ServicioDTO servicioDTO = null;
    if (entity.getService() != null) {
      servicioDTO = ServicioDTO.builder()
          .id(entity.getService().getId())
          .name(entity.getService().getName())
          .price(entity.getService().getPrice())
          .discount(entity.getService().getDiscount())
          .build();
    }

    StablishmentDTO stablishmentDTO = null;
    if (entity.getStablishment() != null) {
      stablishmentDTO = StablishmentDTO.builder()
          .id(entity.getStablishment().getId())
          .name(entity.getStablishment().getName())
          .address(entity.getStablishment().getAddress())
          .build();
    }

    return ScheduleDTO.builder()
        .id(entity.getId())
        .date(entity.getDate())
        .hour(entity.getHour())
        .status(entity.getStatus())
        .createdAt(entity.getCreatedAt())
        .doctor(doctorDTO)
        .service(servicioDTO)
        .stablishment(stablishmentDTO)
        .build();
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
