package com.devluis.services;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

import lombok.Data;

@Service
@Data
public class TurnService {

  private final TurnRepository turnRepository;
  private final PatientRepository patientRepository;
  private final ScheduleRepository scheduleRepository;
  private final com.devluis.repository.OperatorRepository operatorRepository;
  private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

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
    return savedDTO;
  }

  public Page<TurnDTO> getAll(Pageable pageable) {
    return turnRepository.findAll(pageable).map(this::mapToDTO);
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

    if (turn.getStatus() == com.devluis.types.TurnStatus.TURN_TREATED ||
        turn.getStatus() == com.devluis.types.TurnStatus.TURN_CANCELLED) {
      throw new RuntimeException("No puedes cancelar un turno que ya fue atendido o cancelado");
    }

    turn.setStatus(com.devluis.types.TurnStatus.TURN_CANCELLED);

    Turn updated = turnRepository.save(turn);
    TurnDTO updatedDTO = mapToDTO(updated);
    broadcastTurnUpdate(updated, updatedDTO);
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
    if (turn.getSchedule() != null && turn.getSchedule().getService() != null && turn.getSchedule().getDate() != null) {
      String topic = "/topic/turns/" + turn.getSchedule().getService().getId() + "/" + turn.getSchedule().getDate();
      messagingTemplate.convertAndSend(topic, turnDTO);
    }
  }
}
