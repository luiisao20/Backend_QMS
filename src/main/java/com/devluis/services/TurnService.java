package com.devluis.services;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.devluis.dto.DoctorDTO;
import com.devluis.dto.OperatorDTO;
import com.devluis.dto.PatientDTO;
import com.devluis.dto.ScheduleDTO;
import com.devluis.dto.ServicioDTO;
import com.devluis.dto.StablishmentDTO;
import com.devluis.dto.TurnDTO;
import com.devluis.entity.Patient;
import com.devluis.entity.Schedule;
import com.devluis.entity.Turn;
import com.devluis.repository.PatientRepository;
import com.devluis.repository.ScheduleRepository;
import com.devluis.repository.TurnRepository;
import com.devluis.types.ScheduleStatus;
import com.devluis.types.TurnStatus;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.Data;

@Service
@Data
public class TurnService {

  private final TurnRepository turnRepository;
  private final PatientRepository patientRepository;
  private final ScheduleRepository scheduleRepository;
  private final com.devluis.repository.OperatorRepository operatorRepository;
  private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
  private final MailService mailService;

  public TurnDTO create(TurnDTO dto, String authName) {
    // 1. Obtener el paciente a partir de la autenticación
    Patient patient = null;

    UUID uuid = UUID.fromString(authName);
    patient = patientRepository.findById(uuid)
        .orElseThrow(() -> new RuntimeException("Paciente no encontrado por ID"));

    // 2. Obtener el horario
    Schedule schedule = scheduleRepository.findById(dto.getSchedule().getId())
        .orElseThrow(() -> new RuntimeException("Horario no encontrado"));

    if (!schedule.getStatus().equals(ScheduleStatus.STATUS_FREE)) {
      throw new RuntimeException("Este horario ya se encuentra ocupado o cancelado");
    }

    // 3. Determinar el orden y crear el turno
    Long currentTurnsCount = turnRepository.countTurnsByServiceAndDate(schedule.getService().getId(),
        schedule.getDate());
    int nextOrder = (currentTurnsCount != null ? currentTurnsCount.intValue() : 0) + 1;

    Turn turn = Turn.builder()
        .order(nextOrder)
        .patient(patient)
        .schedule(schedule)
        .build();

    Turn saved = turnRepository.save(turn);
    TurnDTO savedDTO = mapToDTO(saved);
    broadcastTurnUpdate(saved, savedDTO);
    sendTurnEmail(patient, schedule, nextOrder);
    return savedDTO;
  }

  public TurnDTO createByStaff(TurnDTO dto, String staffAuthName) {
    if (dto.getPatient() == null || dto.getPatient().getUuid() == null) {
      throw new RuntimeException("Debe proporcionar el uuid del paciente");
    }

    Patient patient = patientRepository.findById(dto.getPatient().getUuid())
        .orElseThrow(() -> new RuntimeException("Paciente no encontrado"));

    Schedule schedule = scheduleRepository.findById(dto.getSchedule().getId())
        .orElseThrow(() -> new RuntimeException("Horario no encontrado"));

    if (!schedule.getStatus().equals(ScheduleStatus.STATUS_FREE)) {
      throw new RuntimeException("Este horario ya se encuentra ocupado o cancelado");
    }

    Long currentTurnsCount = turnRepository.countTurnsByServiceAndDate(schedule.getService().getId(),
        schedule.getDate());
    int nextOrder = (currentTurnsCount != null ? currentTurnsCount.intValue() : 0) + 1;

    // Buscar si el authName corresponde a un Operador
    com.devluis.entity.Operator operator = null;
    try {
      operator = operatorRepository.findById(UUID.fromString(staffAuthName)).orElse(null);
    } catch (Exception e) {
      // ignorar si no es UUID válido
    }

    Turn turn = Turn.builder()
        .order(nextOrder)
        .patient(patient)
        .schedule(schedule)
        .operator(operator)
        .build();

    Turn saved = turnRepository.save(turn);
    TurnDTO savedDTO = mapToDTO(saved);
    broadcastTurnUpdate(saved, savedDTO);
    sendTurnEmail(patient, schedule, nextOrder);
    return savedDTO;
  }

  public Page<TurnDTO> getAll(
      Long stablishmentId,
      UUID doctorId,
      Long serviceId,
      LocalDate date,
      TurnStatus status,
      Pageable pageable) {

    Specification<Turn> spec = (root, query, cb) -> {
      List<Predicate> predicates = new java.util.ArrayList<>();

      Join<Turn, Schedule> scheduleJoin = root.join("schedule", JoinType.LEFT);

      if (stablishmentId != null) {
        predicates.add(cb.equal(scheduleJoin.get("stablishment").get("id"), stablishmentId));
      }

      if (doctorId != null) {
        predicates.add(cb.equal(scheduleJoin.get("doctor").get("uuid"), doctorId));
      }

      if (serviceId != null) {
        predicates.add(cb.equal(scheduleJoin.get("service").get("id"), serviceId));
      }

      if (date != null) {
        predicates.add(cb.equal(scheduleJoin.get("date"), date));
      }

      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };

    return turnRepository.findAll(spec, pageable).map(this::mapToDTO);
  }

  public Page<TurnDTO> getAll(Pageable pageable) {
    return getAll(null, null, null, null, null, pageable);
  }

  public Page<TurnDTO> getTurnsForPatient(
      UUID patientUuid, 
      TurnStatus status, 
      LocalDate fromDate, 
      LocalDate toDate, 
      Pageable pageable) {
    
    Specification<Turn> spec = (root, query, cb) -> {
      List<Predicate> predicates = new java.util.ArrayList<>();

      predicates.add(cb.equal(root.get("patient").get("uuid"), patientUuid));

      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }

      if (fromDate != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("schedule").get("date"), fromDate));
      }

      if (toDate != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("schedule").get("date"), toDate));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };

    return turnRepository.findAll(spec, pageable).map(this::mapToDTO);
  }

  public TurnDTO getById(Long id) {
    Turn turn = turnRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Turno no encontrado"));
    return mapToDTO(turn);
  }

  public TurnDTO markAsTreated(Long turnId, String doctorUuidStr) {
    Turn turn = turnRepository.findById(turnId)
        .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

    if (turn.getSchedule() == null || turn.getSchedule().getDoctor() == null) {
      throw new RuntimeException("El turno no tiene un doctor asignado");
    }

    String assignedDoctorUuid = turn.getSchedule().getDoctor().getUuid().toString();
    if (!assignedDoctorUuid.equals(doctorUuidStr)) {
      throw new RuntimeException("Error de permisos: Este turno no te pertenece");
    }

    turn.setStatus(com.devluis.types.TurnStatus.TURN_TREATED);

    Turn updated = turnRepository.save(turn);
    TurnDTO updatedDTO = mapToDTO(updated);
    broadcastTurnUpdate(updated, updatedDTO);
    return updatedDTO;
  }

  public TurnDTO cancelTurn(Long turnId, String patientUuidStr) {
    Turn turn = turnRepository.findById(turnId)
        .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

    if (turn.getPatient() == null) {
      throw new RuntimeException("El turno no tiene un paciente asociado");
    }

    String assignedPatientUuid = turn.getPatient().getUuid().toString();
    if (!assignedPatientUuid.equals(patientUuidStr)) {
      throw new RuntimeException("Error de permisos: Este turno no te pertenece");
    }

    if (turn.getStatus() == TurnStatus.TURN_TREATED ||
        turn.getStatus() == TurnStatus.TURN_CANCELLED) {
      throw new RuntimeException("No puedes cancelar un turno que ya fue atendido o cancelado");
    }

    turn.setStatus(TurnStatus.TURN_CANCELLED);
    turn.setCancelledAt(OffsetDateTime.now());

    Turn updated = turnRepository.save(turn);
    TurnDTO updatedDTO = mapToDTO(updated);
    broadcastTurnUpdate(updated, updatedDTO);
    return updatedDTO;
  }

  public TurnDTO reassignTurn(Long turnId, Long newScheduleId, String staffAuthName) {
    Turn turn = turnRepository.findById(turnId)
        .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

    if (turn.getStatus() == TurnStatus.TURN_TREATED || turn.getStatus() == TurnStatus.TURN_CANCELLED) {
      throw new RuntimeException("No se puede reasignar un turno que ya fue atendido o cancelado");
    }

    Schedule newSchedule = scheduleRepository.findById(newScheduleId)
        .orElseThrow(() -> new RuntimeException("El nuevo horario no fue encontrado"));

    if (!newSchedule.getStatus().equals(ScheduleStatus.STATUS_FREE)) {
      throw new RuntimeException("El nuevo horario seleccionado no está disponible o se encuentra ocupado");
    }

    Schedule oldSchedule = turn.getSchedule();

    Long currentTurnsCount = turnRepository.countTurnsByServiceAndDate(
        newSchedule.getService().getId(),
        newSchedule.getDate());
    int nextOrder = (currentTurnsCount != null ? currentTurnsCount.intValue() : 0) + 1;

    try {
      operatorRepository.findById(UUID.fromString(staffAuthName)).ifPresent(turn::setOperator);
    } catch (Exception e) {
      // Ignorar si no es UUID de operador
    }

    turn.setSchedule(newSchedule);
    turn.setOrder(nextOrder);
    turn.setStatus(TurnStatus.TURN_PENDING);

    Turn updated = turnRepository.save(turn);
    TurnDTO updatedDTO = mapToDTO(updated);

    if (oldSchedule != null) {
      Turn oldDummy = Turn.builder()
          .schedule(oldSchedule)
          .build();
      broadcastTurnUpdate(oldDummy, updatedDTO);
    }

    broadcastTurnUpdate(updated, updatedDTO);

    if (turn.getPatient() != null) {
      String serviceName = newSchedule.getService() != null ? newSchedule.getService().getName() : "N/A";
      String date = newSchedule.getDate() != null ? newSchedule.getDate().toString() : "N/A";
      String hour = newSchedule.getHour() != null ? newSchedule.getHour().toString() : "N/A";
      String stablishmentName = newSchedule.getStablishment() != null ? newSchedule.getStablishment().getName() : "N/A";
      String doctorFullName = newSchedule.getDoctor() != null
          ? newSchedule.getDoctor().getFirstName() + " " + newSchedule.getDoctor().getLastName()
          : "N/A";

      mailService.sendTurnRescheduledEmail(
          turn.getPatient().getEmail(),
          turn.getPatient().getFirstName(),
          turn.getPatient().getLastName(),
          nextOrder,
          serviceName,
          date,
          hour,
          stablishmentName,
          doctorFullName);
    }

    return updatedDTO;
  }

  public TurnDTO cancelTurnByStaff(Long turnId, String staffAuthName, String reason) {
    Turn turn = turnRepository.findById(turnId)
        .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

    if (turn.getStatus() == TurnStatus.TURN_TREATED || turn.getStatus() == TurnStatus.TURN_CANCELLED) {
      throw new RuntimeException("No se puede cancelar un turno que ya fue atendido o cancelado");
    }

    try {
      operatorRepository.findById(UUID.fromString(staffAuthName)).ifPresent(turn::setOperator);
    } catch (Exception e) {
      // Ignorar si no es UUID de operador
    }

    turn.setStatus(TurnStatus.TURN_CANCELLED);
    turn.setCancelledAt(java.time.OffsetDateTime.now());

    Turn updated = turnRepository.save(turn);
    TurnDTO updatedDTO = mapToDTO(updated);
    broadcastTurnUpdate(updated, updatedDTO);

    if (turn.getPatient() != null && turn.getSchedule() != null) {
      String serviceName = turn.getSchedule().getService() != null ? turn.getSchedule().getService().getName() : "N/A";
      String date = turn.getSchedule().getDate() != null ? turn.getSchedule().getDate().toString() : "N/A";
      String hour = turn.getSchedule().getHour() != null ? turn.getSchedule().getHour().toString() : "N/A";

      mailService.sendTurnCancelledEmail(
          turn.getPatient().getEmail(),
          turn.getPatient().getFirstName(),
          turn.getPatient().getLastName(),
          turn.getOrder(),
          serviceName,
          date,
          hour,
          reason);
    }

    return updatedDTO;
  }

  // Método auxiliar para mapear de Entidad a DTO
  private TurnDTO mapToDTO(Turn entity) {
    PatientDTO patientDTO = null;
    if (entity.getPatient() != null) {
      patientDTO = PatientDTO.builder()
          // Ocultamos el uuid y el password
          .email(entity.getPatient().getEmail())
          .firstName(entity.getPatient().getFirstName())
          .lastName(entity.getPatient().getLastName())
          .ci(entity.getPatient().getCi())
          .build();
    }

    ScheduleDTO scheduleDTO = null;
    if (entity.getSchedule() != null) {
      DoctorDTO doctorDTO = null;
      if (entity.getSchedule().getDoctor() != null) {
        doctorDTO = DoctorDTO.builder()
            .firstName(entity.getSchedule().getDoctor().getFirstName())
            .lastName(entity.getSchedule().getDoctor().getLastName())
            .speciality(entity.getSchedule().getDoctor().getSpeciality())
            .build();
      }

      ServicioDTO servicioDTO = null;
      if (entity.getSchedule().getService() != null) {
        servicioDTO = ServicioDTO.builder()
            .name(entity.getSchedule().getService().getName())
            .price(entity.getSchedule().getService().getPrice())
            .build();
      }

      scheduleDTO = ScheduleDTO.builder()
          .id(entity.getSchedule().getId())
          .date(entity.getSchedule().getDate())
          .hour(entity.getSchedule().getHour())
          .doctor(doctorDTO)
          .service(servicioDTO)
          .stablishment(entity.getSchedule().getStablishment() != null
              ? StablishmentDTO.builder()
                  .name(entity.getSchedule().getStablishment().getName())
                  .address(entity.getSchedule().getStablishment().getAddress())
                  .build()
              : null)
          .build();
    }

    OperatorDTO operatorDTO = null;
    if (entity.getOperator() != null) {
      operatorDTO = OperatorDTO.builder()
          .uuid(entity.getOperator().getUuid())
          .build();
    }

    return TurnDTO.builder()
        .id(entity.getId())
        .order(entity.getOrder())
        .status(entity.getStatus())
        .createdAt(entity.getCreatedAt())
        .finishedAt(entity.getFinishedAt())
        .cancelledAt(entity.getCancelledAt())
        .patient(patientDTO)
        .schedule(scheduleDTO)
        .operator(operatorDTO)
        .build();
  }

  private void broadcastTurnUpdate(Turn turn, TurnDTO turnDTO) {
    // Canal por establecimiento (para pantallas de sala de espera)
    if (turn.getSchedule() != null && turn.getSchedule().getStablishment() != null && turn.getSchedule().getDate() != null) {
      String topic = "/topic/stablishment/" + turn.getSchedule().getStablishment().getId() + "/" + turn.getSchedule().getDate();
      messagingTemplate.convertAndSend(topic, turnDTO);
    }

    // Canal por doctor, servicio y fecha usando autenticación
    if (turn.getSchedule() != null && turn.getSchedule().getDoctor() != null && turn.getSchedule().getService() != null && turn.getSchedule().getDate() != null) {
      String doctorUuid = turn.getSchedule().getDoctor().getUuid().toString();
      // El cliente se suscribirá a: /user/topic/service/{serviceId}/{date}
      String destination = "/topic/service/" + turn.getSchedule().getService().getId() + "/" + turn.getSchedule().getDate();
      messagingTemplate.convertAndSendToUser(doctorUuid, destination, turnDTO);
    }
  }

  private void sendTurnEmail(Patient patient, Schedule schedule, int turnOrder) {
    try {
      String serviceName = schedule.getService() != null ? schedule.getService().getName() : "N/A";
      String date = schedule.getDate() != null ? schedule.getDate().toString() : "N/A";
      String hour = schedule.getHour() != null ? schedule.getHour().toString() : "N/A";
      String stablishmentName = schedule.getStablishment() != null ? schedule.getStablishment().getName() : "N/A";
      String doctorFullName = schedule.getDoctor() != null
          ? schedule.getDoctor().getFirstName() + " " + schedule.getDoctor().getLastName()
          : "N/A";

      mailService.sendTurnCreatedEmail(
          patient.getEmail(),
          patient.getFirstName(),
          patient.getLastName(),
          patient.getCi(),
          turnOrder,
          serviceName,
          date,
          hour,
          stablishmentName,
          doctorFullName
      );
    } catch (Exception e) {
      // Log del error pero no interrumpimos la creación del turno
      System.err.println("Error al enviar correo de notificación: " + e.getMessage());
    }
  }
}
