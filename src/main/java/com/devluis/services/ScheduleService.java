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
  private final com.devluis.repository.TurnRepository turnRepository;

  public ScheduleDTO create(ScheduleDTO dto) {
    Doctor doctor = doctorRepository.findById(dto.getDoctor().getUuid())
        .orElseThrow(() -> new RuntimeException("Doctor no encontrado"));

    Servicio servicio = serviceRepository.findById(dto.getService().getId())
        .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));

    com.devluis.entity.Stablishment stablishment = stablishmentRepository.findById(dto.getStablishment().getId())
        .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));

    boolean serviceInStablishment = stablishment.getServices() != null &&
        stablishment.getServices().stream().anyMatch(s -> s.getId().equals(servicio.getId()));
    if (!serviceInStablishment) {
        throw new RuntimeException("El servicio seleccionado no está disponible en este establecimiento");
    }

    if (doctor.getServices() == null || !doctor.getServices().stream().anyMatch(s -> s.getId().equals(servicio.getId()))) {
        throw new RuntimeException("El doctor seleccionado no tiene asignado este servicio");
    }
    if (doctor.getStablishments() == null || !doctor.getStablishments().stream().anyMatch(st -> st.getId().equals(stablishment.getId()))) {
        throw new RuntimeException("El doctor seleccionado no está asignado a este establecimiento");
    }

    if (scheduleRepository.existsByDoctorUuidAndDateAndHour(doctor.getUuid(), dto.getDate(), dto.getHour())) {
        throw new RuntimeException("El doctor ya tiene un horario asignado para la fecha " + dto.getDate() + " a las " + dto.getHour());
    }

    if (scheduleRepository.existsByServiceIdAndStablishmentIdAndDoctorUuidAndDateAndHour(servicio.getId(), stablishment.getId(), doctor.getUuid(), dto.getDate(), dto.getHour())) {
        throw new RuntimeException("Ya existe un horario registrado para este servicio y establecimiento en la fecha " + dto.getDate() + " a las " + dto.getHour());
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
      Long serviceId,
      java.time.LocalDate from,
      java.time.LocalDate to,
      com.devluis.types.ScheduleStatus status,
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

      // `date` (exact day, used by the admin screens) and `from`/`to` (a
      // range, used by the Flutter booking flow) are independent, additive
      // (AND) filters, exactly like every other filter below. No client
      // sends both today, but if one ever did, the result would be the
      // intersection of "exactly this day" AND "inside this range" — a
      // stricter, possibly empty, filter rather than an error or a
      // last-one-wins override.
      if (date != null) {
        predicates.add(cb.equal(root.get("date"), date));
      }

      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("date"), from));
      }

      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("date"), to));
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

      if (serviceId != null) {
        predicates.add(cb.equal(root.get("service").get("id"), serviceId));
      }

      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }

      return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    };

    return scheduleRepository.findAll(spec, pageable).map(this::mapToDTO);
  }

  public Page<ScheduleDTO> getAll(Pageable pageable) {
    return getAll(null, null, null, null, null, null, null, null, pageable);
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

    if (schedule.getStablishment() != null && schedule.getService() != null) {
      boolean serviceInStablishment = schedule.getStablishment().getServices() != null &&
          schedule.getStablishment().getServices().stream().anyMatch(s -> s.getId().equals(schedule.getService().getId()));
      if (!serviceInStablishment) {
        throw new RuntimeException("El servicio seleccionado no está disponible en este establecimiento");
      }
    }

    if (schedule.getDoctor() != null) {
      if (scheduleRepository.existsByDoctorUuidAndDateAndHourAndIdNot(schedule.getDoctor().getUuid(), schedule.getDate(), schedule.getHour(), schedule.getId())) {
        throw new RuntimeException("El doctor ya tiene otro horario asignado para la fecha " + schedule.getDate() + " a las " + schedule.getHour());
      }
    }

    Schedule updated = scheduleRepository.save(schedule);
    return mapToDTO(updated);
  }

  public void delete(Long id) {
    if (!scheduleRepository.existsById(id)) {
      throw new RuntimeException("Horario no encontrado");
    }
    if (turnRepository.existsByScheduleId(id)) {
      throw new RuntimeException(
          "No se puede eliminar el horario porque tiene turnos reservados. Cancele o reasigne los turnos antes de eliminarlo.");
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

    boolean serviceInStablishment = stablishment.getServices() != null &&
        stablishment.getServices().stream().anyMatch(s -> s.getId().equals(servicio.getId()));
    if (!serviceInStablishment) {
        throw new RuntimeException("El servicio seleccionado no está disponible en este establecimiento");
    }

    Doctor doctor = null;
    if (body.getDoctorId() != null) {
      doctor = doctorRepository.findById(body.getDoctorId())
          .orElseThrow(() -> new RuntimeException("Doctor no encontrado"));

      if (doctor.getServices() == null || !doctor.getServices().stream().anyMatch(s -> s.getId().equals(servicio.getId()))) {
          throw new RuntimeException("El doctor seleccionado no tiene asignado este servicio");
      }
      if (doctor.getStablishments() == null || !doctor.getStablishments().stream().anyMatch(st -> st.getId().equals(stablishment.getId()))) {
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

      boolean alreadyExists = false;
      if (doctor != null) {
        alreadyExists = scheduleRepository.existsByDoctorUuidAndDateAndHour(doctor.getUuid(), body.getDate(), current);
      } else {
        alreadyExists = scheduleRepository.existsByServiceIdAndStablishmentIdAndDateAndHour(servicio.getId(), stablishment.getId(), body.getDate(), current);
      }

      if (!alreadyExists) {
        Schedule schedule = Schedule.builder()
            .date(body.getDate())
            .hour(current)
            .doctor(doctor)
            .service(servicio)
            .stablishment(stablishment)
            .status(com.devluis.types.ScheduleStatus.STATUS_FREE)
            .build();
        generated.add(schedule);
      }

      current = slotEnd;
    }

    if (generated.isEmpty()) {
      throw new RuntimeException("Ya existen horarios creados para todos los bloques de esta fecha");
    }

    java.util.List<Schedule> saved = scheduleRepository.saveAll(generated);
    return saved.stream()
        .sorted(java.util.Comparator.comparing(Schedule::getDate).thenComparing(Schedule::getHour))
        .map(this::mapToDTO)
        .toList();
  }
}
