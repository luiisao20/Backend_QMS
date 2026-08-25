package com.devluis.services;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.devluis.dto.DoctorDTO;
import com.devluis.dto.ScheduleTemplateDTO;
import com.devluis.dto.ServicioDTO;
import com.devluis.dto.StablishmentDTO;
import com.devluis.entity.Doctor;
import com.devluis.entity.ScheduleTemplate;
import com.devluis.entity.Servicio;
import com.devluis.entity.Stablishment;
import com.devluis.repository.DoctorRepository;
import com.devluis.repository.ScheduleTemplateRepository;
import com.devluis.repository.ServiceRepository;
import com.devluis.repository.StablishmentRepository;

import jakarta.persistence.criteria.Predicate;
import lombok.Data;

@Service
@Data
public class ScheduleTemplateService {
  private final ScheduleTemplateRepository scheduleTemplateRepository;
  private final StablishmentRepository stablishmentRepository;
  private final ServiceRepository serviceRepository;
  private final DoctorRepository doctorRepository;
  private final com.devluis.repository.ScheduleRepository scheduleRepository;

  public ScheduleTemplateDTO create(ScheduleTemplateDTO dto) {
    Stablishment stablishment = resolveStablishment(dto);
    Servicio servicio = resolveServicio(dto);
    validateServiceInStablishment(stablishment, servicio);
    Doctor doctor = resolveDoctor(dto);
    validateDoctorAssignments(stablishment, servicio, doctor);
    validateTimeRange(dto);
    validateValidityWindow(dto);
    rejectIfOverlapping(stablishment, servicio, doctor, dto.getDayOfWeek(), dto.getStartTime(), dto.getEndTime(),
        dto.getValidFrom(), dto.getValidUntil(), null);

    ScheduleTemplate template = ScheduleTemplate.builder()
        .stablishment(stablishment)
        .servicio(servicio)
        .doctor(doctor)
        .dayOfWeek(dto.getDayOfWeek())
        .startTime(dto.getStartTime())
        .endTime(dto.getEndTime())
        .slotIntervalMinutes(dto.getSlotIntervalMinutes())
        .validFrom(dto.getValidFrom())
        .validUntil(dto.getValidUntil())
        .build();

    ScheduleTemplate saved = scheduleTemplateRepository.save(template);
    return mapToDTO(saved);
  }

  public Page<ScheduleTemplateDTO> getAll(Long stablishmentId, Long serviceId, UUID doctorId, Pageable pageable) {
    Specification<ScheduleTemplate> spec = (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (stablishmentId != null) {
        predicates.add(cb.equal(root.get("stablishment").get("id"), stablishmentId));
      }
      if (serviceId != null) {
        predicates.add(cb.equal(root.get("servicio").get("id"), serviceId));
      }
      if (doctorId != null) {
        predicates.add(cb.equal(root.get("doctor").get("uuid"), doctorId));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };

    return scheduleTemplateRepository.findAll(spec, pageable).map(this::mapToDTO);
  }

  public Page<ScheduleTemplateDTO> getAll(Pageable pageable) {
    return getAll(null, null, null, pageable);
  }

  public ScheduleTemplateDTO getById(Long id) {
    return mapToDTO(findByIdOrThrow(id));
  }

  public ScheduleTemplateDTO update(Long id, ScheduleTemplateDTO dto) {
    ScheduleTemplate template = findByIdOrThrow(id);

    Stablishment stablishment = resolveStablishment(dto);
    Servicio servicio = resolveServicio(dto);
    validateServiceInStablishment(stablishment, servicio);
    Doctor doctor = resolveDoctor(dto);
    validateDoctorAssignments(stablishment, servicio, doctor);
    validateTimeRange(dto);
    validateValidityWindow(dto);
    rejectIfOverlapping(stablishment, servicio, doctor, dto.getDayOfWeek(), dto.getStartTime(), dto.getEndTime(),
        dto.getValidFrom(), dto.getValidUntil(), id);

    template.setStablishment(stablishment);
    template.setServicio(servicio);
    template.setDoctor(doctor);
    template.setDayOfWeek(dto.getDayOfWeek());
    template.setStartTime(dto.getStartTime());
    template.setEndTime(dto.getEndTime());
    template.setSlotIntervalMinutes(dto.getSlotIntervalMinutes());
    template.setValidFrom(dto.getValidFrom());
    template.setValidUntil(dto.getValidUntil());

    // Sweep old free schedules from today onwards before applying changes
    sweepTemplateSchedules(template);

    template.setStablishment(stablishment);
    template.setServicio(servicio);
    template.setDoctor(doctor);
    template.setDayOfWeek(dto.getDayOfWeek());
    template.setStartTime(dto.getStartTime());
    template.setEndTime(dto.getEndTime());
    template.setSlotIntervalMinutes(dto.getSlotIntervalMinutes());
    template.setValidFrom(dto.getValidFrom());
    template.setValidUntil(dto.getValidUntil());

    ScheduleTemplate updated = scheduleTemplateRepository.save(template);
    return mapToDTO(updated);
  }

  public void delete(Long id) {
    ScheduleTemplate template = scheduleTemplateRepository.findById(id).orElse(null);
    if (template == null) {
      throw new RuntimeException("Plantilla de horario no encontrada");
    }
    
    // Sweep free schedules corresponding to this template
    sweepTemplateSchedules(template);
    
    scheduleTemplateRepository.deleteById(id);
  }

  private void sweepTemplateSchedules(ScheduleTemplate template) {
    List<com.devluis.entity.Schedule> candidates = scheduleRepository.findAll((root, query, cb) -> {
        List<Predicate> preds = new ArrayList<>();
        preds.add(cb.equal(root.get("stablishment").get("id"), template.getStablishment().getId()));
        preds.add(cb.equal(root.get("service").get("id"), template.getServicio().getId()));
        
        if (template.getDoctor() != null) {
            preds.add(cb.equal(root.get("doctor").get("uuid"), template.getDoctor().getUuid()));
        } else {
            preds.add(cb.isNull(root.get("doctor")));
        }
        
        preds.add(cb.greaterThanOrEqualTo(root.get("date"), LocalDate.now()));
        return cb.and(preds.toArray(new Predicate[0]));
    });

    List<com.devluis.entity.Schedule> toDelete = candidates.stream()
        .filter(s -> s.getDate().getDayOfWeek() == template.getDayOfWeek())
        .filter(s -> !s.getHour().isBefore(template.getStartTime()) && s.getHour().isBefore(template.getEndTime()))
        .filter(s -> s.getStatus() == com.devluis.types.ScheduleStatus.STATUS_FREE)
        .toList();

    scheduleRepository.deleteAll(toDelete);
  }

  private ScheduleTemplate findByIdOrThrow(Long id) {
    return scheduleTemplateRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Plantilla de horario no encontrada"));
  }

  private Stablishment resolveStablishment(ScheduleTemplateDTO dto) {
    return stablishmentRepository.findById(dto.getStablishment().getId())
        .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));
  }

  private Servicio resolveServicio(ScheduleTemplateDTO dto) {
    return serviceRepository.findById(dto.getServicio().getId())
        .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));
  }

  private Doctor resolveDoctor(ScheduleTemplateDTO dto) {
    if (dto.getDoctor() == null || dto.getDoctor().getUuid() == null) {
      return null;
    }
    return doctorRepository.findById(dto.getDoctor().getUuid())
        .orElseThrow(() -> new RuntimeException("Doctor no encontrado"));
  }

  private void validateServiceInStablishment(Stablishment stablishment, Servicio servicio) {
    boolean serviceInStablishment = stablishment.getServices() != null
        && stablishment.getServices().stream().anyMatch(s -> s.getId().equals(servicio.getId()));
    if (!serviceInStablishment) {
      throw new RuntimeException("El servicio seleccionado no está disponible en este establecimiento");
    }
  }

  private void validateDoctorAssignments(Stablishment stablishment, Servicio servicio, Doctor doctor) {
    if (doctor == null) {
      return;
    }
    if (doctor.getServices() == null || doctor.getServices().stream().noneMatch(s -> s.getId().equals(servicio.getId()))) {
      throw new RuntimeException("El doctor seleccionado no tiene asignado este servicio");
    }
    if (doctor.getStablishments() == null
        || doctor.getStablishments().stream().noneMatch(st -> st.getId().equals(stablishment.getId()))) {
      throw new RuntimeException("El doctor seleccionado no está asignado a este establecimiento");
    }
  }

  private void validateTimeRange(ScheduleTemplateDTO dto) {
    if (!dto.getStartTime().isBefore(dto.getEndTime())) {
      throw new RuntimeException("La hora de fin debe ser posterior a la hora de inicio");
    }
  }

  private void validateValidityWindow(ScheduleTemplateDTO dto) {
    if (dto.getValidUntil() != null && dto.getValidUntil().isBefore(dto.getValidFrom())) {
      throw new RuntimeException(
          "La fecha de fin de vigencia no puede ser anterior a la fecha de inicio de vigencia");
    }
  }

  // Rejects the write outright instead of silently deactivating the older
  // template or letting both stack — see ScheduleTemplate's docblock.
  private void rejectIfOverlapping(Stablishment stablishment, Servicio servicio, Doctor doctor, DayOfWeek dayOfWeek,
      LocalTime startTime, LocalTime endTime, LocalDate validFrom, LocalDate validUntil, Long excludeId) {
    boolean overlaps = doctor != null
        ? scheduleTemplateRepository.existsOverlappingForDoctor(
            doctor.getUuid(), dayOfWeek, startTime, endTime, validFrom, validUntil, excludeId)
        : scheduleTemplateRepository.existsOverlappingForPool(
            stablishment.getId(), servicio.getId(), dayOfWeek, startTime, endTime, validFrom, validUntil, excludeId);

    if (overlaps) {
      throw new RuntimeException(
          "Ya existe una plantilla de horario que se superpone en ese día y horario. "
              + "Ajuste el horario o finalice la plantilla existente antes de crear esta.");
    }
  }

  private ScheduleTemplateDTO mapToDTO(ScheduleTemplate entity) {
    StablishmentDTO stablishmentDTO = null;
    if (entity.getStablishment() != null) {
      stablishmentDTO = StablishmentDTO.builder()
          .id(entity.getStablishment().getId())
          .name(entity.getStablishment().getName())
          .address(entity.getStablishment().getAddress())
          .build();
    }

    ServicioDTO servicioDTO = null;
    if (entity.getServicio() != null) {
      servicioDTO = ServicioDTO.builder()
          .id(entity.getServicio().getId())
          .name(entity.getServicio().getName())
          .price(entity.getServicio().getPrice())
          .build();
    }

    DoctorDTO doctorDTO = null;
    if (entity.getDoctor() != null) {
      doctorDTO = DoctorDTO.builder()
          .uuid(entity.getDoctor().getUuid())
          .firstName(entity.getDoctor().getFirstName())
          .lastName(entity.getDoctor().getLastName())
          .build();
    }

    return ScheduleTemplateDTO.builder()
        .id(entity.getId())
        .stablishment(stablishmentDTO)
        .servicio(servicioDTO)
        .doctor(doctorDTO)
        .dayOfWeek(entity.getDayOfWeek())
        .startTime(entity.getStartTime())
        .endTime(entity.getEndTime())
        .slotIntervalMinutes(entity.getSlotIntervalMinutes())
        .validFrom(entity.getValidFrom())
        .validUntil(entity.getValidUntil())
        .createdAt(entity.getCreatedAt())
        .build();
  }
}
