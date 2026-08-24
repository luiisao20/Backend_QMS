package com.devluis.services;

import com.devluis.dto.DoctorDTO;
import com.devluis.dto.ScheduleDTO;
import com.devluis.dto.ServicioDTO;
import com.devluis.entity.Doctor;
import com.devluis.entity.Schedule;
import com.devluis.entity.Servicio;
import com.devluis.repository.DoctorRepository;
import com.devluis.repository.ScheduleRepository;
import com.devluis.repository.ServiceRepository;

import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@Data
public class ScheduleService {

  private final ScheduleRepository scheduleRepository;
  private final DoctorRepository doctorRepository;
  private final ServiceRepository serviceRepository;
  private final com.devluis.repository.StablishmentRepository stablishmentRepository;

  public ScheduleDTO create(ScheduleDTO dto) {
    Doctor doctor = doctorRepository.findById(dto.getDoctor().getUuid())
        .orElseThrow(() -> new RuntimeException("Doctor no encontrado"));

    Servicio servicio = serviceRepository.findById(dto.getService().getId())
        .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));

    com.devluis.entity.Stablishment stablishment = stablishmentRepository.findById(dto.getStablishment().getId())
        .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));

    if (doctor.getServices() == null || !doctor.getServices().contains(servicio)) {
        throw new RuntimeException("El doctor seleccionado no tiene asignado este servicio");
    }
    if (doctor.getStablishments() == null || !doctor.getStablishments().contains(stablishment)) {
        throw new RuntimeException("El doctor seleccionado no está asignado a este establecimiento");
    }

    Schedule schedule = Schedule.builder()
        .date(dto.getDate())
        .hour(dto.getHour())
        .doctor(doctor)
        .service(servicio)
        .stablishment(stablishment)
        .build();

    Schedule saved = scheduleRepository.save(schedule);
    return mapToDTO(saved);
  }

  public Page<ScheduleDTO> getAll(
      java.time.LocalDate date, 
      Long stablishmentId, 
      java.util.UUID doctorId, 
      String doctorName, 
      Pageable pageable) {
    
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

      if (date != null) {
        predicates.add(cb.equal(root.get("date"), date));
      }

      if (stablishmentId != null) {
        predicates.add(cb.equal(root.get("stablishment").get("id"), stablishmentId));
      }

      if (doctorId != null) {
        predicates.add(cb.equal(root.get("doctor").get("uuid"), doctorId));
      }

      if (doctorName != null && !doctorName.trim().isEmpty()) {
        String pattern = "%" + doctorName.trim().toLowerCase() + "%";
        jakarta.persistence.criteria.Join<Schedule, Doctor> doctorJoin = root.join("doctor", jakarta.persistence.criteria.JoinType.LEFT);
        jakarta.persistence.criteria.Expression<String> fullName = cb.concat(cb.concat(cb.lower(doctorJoin.get("firstName")), " "), cb.lower(doctorJoin.get("lastName")));
        predicates.add(cb.or(
            cb.like(cb.lower(doctorJoin.get("firstName")), pattern),
            cb.like(cb.lower(doctorJoin.get("lastName")), pattern),
            cb.like(fullName, pattern)
        ));
      }

      return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    };

    return scheduleRepository.findAll(spec, pageable).map(this::mapToDTO);
  }

  public Page<ScheduleDTO> getAll(Pageable pageable) {
    return getAll(null, null, null, null, pageable);
  }

  public ScheduleDTO getById(Long id) {
    Schedule schedule = scheduleRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Horario no encontrado"));
    return mapToDTO(schedule);
  }

  public ScheduleDTO update(Long id, ScheduleDTO dto) {
    Schedule schedule = scheduleRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Horario no encontrado"));

    schedule.setDate(dto.getDate());
    schedule.setHour(dto.getHour());

    if (dto.getStatus() != null) {
      schedule.setStatus(dto.getStatus());
    }

    if (dto.getDoctor() != null && dto.getDoctor().getUuid() != null) {
      Doctor doctor = doctorRepository.findById(dto.getDoctor().getUuid())
          .orElseThrow(() -> new RuntimeException("Doctor no encontrado"));
      schedule.setDoctor(doctor);
    }

    if (dto.getService() != null && dto.getService().getId() != null) {
      Servicio servicio = serviceRepository.findById(dto.getService().getId())
          .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));
      schedule.setService(servicio);
    }

    if (dto.getStablishment() != null && dto.getStablishment().getId() != null) {
      com.devluis.entity.Stablishment stablishment = stablishmentRepository.findById(dto.getStablishment().getId())
          .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));
      schedule.setStablishment(stablishment);
    }

    Schedule updated = scheduleRepository.save(schedule);
    return mapToDTO(updated);
  }

  public void delete(Long id) {
    if (!scheduleRepository.existsById(id)) {
      throw new RuntimeException("Horario no encontrado");
    }
    scheduleRepository.deleteById(id);
  }

  private ScheduleDTO mapToDTO(Schedule entity) {
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
          .build();
    }

    com.devluis.dto.StablishmentDTO stablishmentDTO = null;
    if (entity.getStablishment() != null) {
      stablishmentDTO = com.devluis.dto.StablishmentDTO.builder()
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
  public java.util.List<ScheduleDTO> generateSchedules(com.devluis.types.GenerateSchedulesBody body) {
    Servicio servicio = serviceRepository.findById(body.getServiceId())
        .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));

    com.devluis.entity.Stablishment stablishment = stablishmentRepository.findById(body.getStablishmentId())
        .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));

    Doctor doctor = null;
    if (body.getDoctorId() != null) {
      doctor = doctorRepository.findById(body.getDoctorId())
          .orElseThrow(() -> new RuntimeException("Doctor no encontrado"));

      if (doctor.getServices() == null || !doctor.getServices().contains(servicio)) {
          throw new RuntimeException("El doctor seleccionado no tiene asignado este servicio");
      }
      if (doctor.getStablishments() == null || !doctor.getStablishments().contains(stablishment)) {
          throw new RuntimeException("El doctor seleccionado no está asignado a este establecimiento");
      }
    }

    java.time.LocalTime current = java.time.LocalTime.of(8, 0);
    java.time.LocalTime end = java.time.LocalTime.of(17, 0);
    java.time.LocalTime breakStart = java.time.LocalTime.of(12, 0);
    java.time.LocalTime breakEnd = java.time.LocalTime.of(13, 0);

    java.util.List<Schedule> generated = new java.util.ArrayList<>();

    while (!current.plusMinutes(body.getIntervalMinutes()).isAfter(end)) {
      java.time.LocalTime slotEnd = current.plusMinutes(body.getIntervalMinutes());

      if (current.isBefore(breakEnd) && slotEnd.isAfter(breakStart)) {
        current = breakEnd;
        continue;
      }

      Schedule schedule = Schedule.builder()
          .date(body.getDate())
          .hour(current)
          .doctor(doctor)
          .service(servicio)
          .stablishment(stablishment)
          .status(com.devluis.types.ScheduleStatus.STATUS_FREE)
          .build();
      generated.add(schedule);

      current = slotEnd;
    }

    java.util.List<Schedule> saved = scheduleRepository.saveAll(generated);
    return saved.stream()
        .sorted(java.util.Comparator.comparing(Schedule::getDate).thenComparing(Schedule::getHour))
        .map(this::mapToDTO)
        .toList();
  }
}
