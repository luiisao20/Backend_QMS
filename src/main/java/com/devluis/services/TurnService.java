package com.devluis.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devluis.dto.ConsultorioDTO;
import com.devluis.dto.DoctorDTO;
import com.devluis.dto.LongStatusCountRow;
import com.devluis.dto.OperatorDTO;
import com.devluis.dto.PatientDTO;
import com.devluis.dto.ScheduleDTO;
import com.devluis.dto.ServicioDTO;
import com.devluis.dto.StablishmentDTO;
import com.devluis.dto.TurnBoardDTO;
import com.devluis.dto.TurnDTO;
import com.devluis.dto.TurnDailyCountsDTO;
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
import lombok.extern.slf4j.Slf4j;

@Service
@Data
@Slf4j
public class TurnService {

  private final TurnRepository turnRepository;
  private final PatientRepository patientRepository;
  private final ScheduleRepository scheduleRepository;
  private final com.devluis.repository.OperatorRepository operatorRepository;
  private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
  private final MailService mailService;
  private final com.devluis.repository.ConsultorioRepository consultorioRepository;

  /**
   * Solo para saber SI quien llama es un medico.
   *
   * `/waiting` e `/in-treatment` los usan recepcion y el medico desde pantallas
   * distintas. La capa de seguridad ya decide quien puede tocar el endpoint;
   * lo que no puede decidir es de QUIEN es el turno, y esa diferencia importa:
   * un operador atiende la cola entera de su sede, un medico solo la suya.
   */
  private final com.devluis.repository.DoctorRepository doctorRepository;

  @Transactional
  public TurnDTO create(TurnDTO dto, String authName) {
    // 1. Obtener el paciente a partir de la autenticación
    Patient patient = null;

    UUID uuid = UUID.fromString(authName);
    patient = patientRepository.findById(uuid)
        .orElseThrow(() -> new RuntimeException("Paciente no encontrado por ID"));

    // 2. Obtener el horario
    Schedule schedule = scheduleRepository.findById(dto.getSchedule().getId())
        .orElseThrow(() -> new RuntimeException("Horario no encontrado"));

    // Antes que el chequeo de ocupacion, a proposito: un cupo pasado NUNCA
    // vuelve a ser reservable, mientras que uno ocupado si puede liberarse. Se
    // reporta primero la razon permanente.
    requireUpcoming(schedule, LocalDateTime.now());

    if (!schedule.getStatus().equals(ScheduleStatus.STATUS_FREE)) {
      throw new RuntimeException("Este horario ya se encuentra ocupado o cancelado");
    }

    // Ocupa el horario de forma atómica (bloqueo optimista, ver
    // occupySchedule). Si otra reserva ganó la carrera entre el chequeo de
    // arriba y este punto, esto lanza un mensaje claro en español en lugar de
    // permitir que se cree un turno duplicado sobre el mismo horario.
    occupySchedule(schedule);

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

  /**
   * Rechaza un cupo cuya hora de inicio ya quedo atras.
   *
   * <p>
   * Sin esto, el unico filtro contra cupos pasados vivia en la UI, y un
   * filtro que solo existe en el cliente no es una regla: {@code POST
   * /api/turns} con el id de un cupo de las 08:00 se aceptaba a las 15:00, y
   * dejaba en la agenda del doctor un turno para una hora que no existe. La
   * app de Flutter tambien los oculta ahora, pero eso es comodidad; esto es la
   * regla.
   *
   * <p>
   * Compara {@code date} Y {@code hour} juntos, nunca la hora sola: un cupo
   * de ayer a las 09:00 sigue siendo pasado a las 08:00 de hoy.
   *
   * <p>
   * El instante llega por parametro en lugar de leerse aca adentro para que
   * el limite exacto sea testeable al minuto — ver {@code TurnServiceTest}.
   * Un cupo que arranca EXACTAMENTE ahora se rechaza: es el mismo criterio que
   * usa la app para dibujar la grilla, y que las dos puntas corten igual evita
   * que el paciente vea un chip que el servidor va a rechazar.
   */
  static void requireUpcoming(Schedule schedule, LocalDateTime now) {
    LocalDateTime startsAt = LocalDateTime.of(schedule.getDate(), schedule.getHour());
    if (!startsAt.isAfter(now)) {
      throw new RuntimeException("Este horario ya paso. Elija un cupo posterior a la hora actual.");
    }
  }

  @Transactional
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

    occupySchedule(schedule);

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

    if (turn.getStatus() == TurnStatus.TURN_TREATED || turn.getStatus() == TurnStatus.TURN_CANCELLED) {
      throw new RuntimeException("No se puede marcar como atendido un turno que ya fue atendido o cancelado");
    }

    if (turn.getSchedule() == null || turn.getSchedule().getDoctor() == null) {
      throw new RuntimeException("El turno no tiene un doctor asignado");
    }

    String assignedDoctorUuid = turn.getSchedule().getDoctor().getUuid().toString();
    if (!assignedDoctorUuid.equals(doctorUuidStr)) {
      throw new RuntimeException("Error de permisos: Este turno no te pertenece");
    }

    turn.setStatus(TurnStatus.TURN_TREATED);

    Turn updated = turnRepository.save(turn);
    TurnDTO updatedDTO = mapToDTO(updated);
    broadcastTurnUpdate(updated, updatedDTO);
    return updatedDTO;
  }

  public TurnDTO markAsTreatedAdmin(Long turnId) {
    Turn turn = turnRepository.findById(turnId)
        .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

    if (turn.getStatus() == TurnStatus.TURN_TREATED || turn.getStatus() == TurnStatus.TURN_CANCELLED) {
      throw new RuntimeException("No se puede marcar como atendido un turno que ya fue atendido o cancelado");
    }

    if (turn.getSchedule() == null || turn.getSchedule().getDoctor() == null) {
      throw new RuntimeException("El turno no tiene un doctor asignado");
    }

    turn.setStatus(TurnStatus.TURN_TREATED);

    Turn updated = turnRepository.save(turn);
    TurnDTO updatedDTO = mapToDTO(updated);
    broadcastTurnUpdate(updated, updatedDTO);
    return updatedDTO;
  }

  /**
   * Si quien llama es un MEDICO, el turno tiene que ser suyo.
   *
   * Un operador o un admin pasan de largo: es su trabajo mover la cola entera
   * de la sede, y la regla de seguridad ya los autorizo. Un medico, en cambio,
   * llega hasta aca porque su propio panel necesita estos endpoints — y sin
   * esta comprobacion podria hacer ingreso y llamar a los pacientes de
   * cualquier otro medico de la clinica.
   *
   * Es exactamente la regla que markAsTreated ya aplicaba; lo unico nuevo es
   * que ahora tambien cubre el ingreso y el llamado.
   *
   * El principal que NO parsea como UUID no se rechaza aca: eso ya lo resolvio
   * la capa de seguridad, y convertirlo en un error de pertenencia diria algo
   * falso sobre lo que paso.
   */
  private void requireOwnershipWhenDoctor(Turn turn, String authName) {
    UUID callerUuid;
    try {
      callerUuid = UUID.fromString(authName);
    } catch (Exception e) {
      return;
    }

    if (doctorRepository.findById(callerUuid).isEmpty()) {
      return;
    }

    String assignedDoctorUuid = turn.getSchedule() != null && turn.getSchedule().getDoctor() != null
        ? turn.getSchedule().getDoctor().getUuid().toString()
        : null;

    if (!callerUuid.toString().equals(assignedDoctorUuid)) {
      throw new RuntimeException("Error de permisos: Este turno no te pertenece");
    }
  }

  // Check-in: the patient arrived and registered at the counter.
  public TurnDTO markAsWaiting(Long turnId, String staffAuthName) {
    Turn turn = turnRepository.findById(turnId)
        .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

    requireOwnershipWhenDoctor(turn, staffAuthName);

    if (turn.getStatus() != TurnStatus.TURN_PENDING) {
      throw new RuntimeException("Solo se puede registrar el ingreso de un turno que está pendiente");
    }

    try {
      operatorRepository.findById(UUID.fromString(staffAuthName)).ifPresent(turn::setOperator);
    } catch (Exception e) {
      // Ignore if the authenticated principal is not an Operator UUID
    }

    turn.setStatus(TurnStatus.TURN_WAITNG);

    Turn updated = turnRepository.save(turn);
    TurnDTO updatedDTO = mapToDTO(updated);
    broadcastTurnUpdate(updated, updatedDTO);
    return updatedDTO;
  }

  /**
   * Sobrecarga historica: llamar sin elegir consultorio usa el del cupo.
   * Existe para no romper a los llamadores previos a que el consultorio
   * existiera; el camino real de la UI pasa el consultorioId.
   */
  public TurnDTO markAsInTreatment(Long turnId, String staffAuthName) {
    return markAsInTreatment(turnId, null, staffAuthName);
  }

  /**
   * El llamado: WAITNG -> IN_TREATMENT, con el consultorio por el que sale.
   *
   * consultorioId null NO significa "sin consultorio": significa "el que ya
   * traia el cupo", que a su vez viene de la plantilla que definio un admin.
   * El operador solo tiene que intervenir cuando el medico se mudo.
   */
  public TurnDTO markAsInTreatment(Long turnId, Long consultorioId, String staffAuthName) {
    Turn turn = turnRepository.findById(turnId)
        .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

    requireOwnershipWhenDoctor(turn, staffAuthName);

    if (turn.getStatus() != TurnStatus.TURN_WAITNG) {
      throw new RuntimeException("Solo se puede iniciar la atención de un turno que está en sala de espera");
    }

    turn.setConsultorio(resolveConsultorioForCall(turn, consultorioId));
    turn.setCalledAt(java.time.OffsetDateTime.now());

    try {
      operatorRepository.findById(UUID.fromString(staffAuthName)).ifPresent(turn::setOperator);
    } catch (Exception e) {
      // Ignore if the authenticated principal is not an Operator UUID
    }

    turn.setStatus(TurnStatus.TURN_IN_TREATMENT);

    Turn updated = turnRepository.save(turn);
    TurnDTO updatedDTO = mapToDTO(updated);
    broadcastTurnUpdate(updated, updatedDTO);
    return updatedDTO;
  }

  @Transactional
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
    // La cancelación en sí ya quedó guardada arriba: liberar el horario es un
    // efecto secundario best-effort (ver releaseScheduleIfUnclaimed) y nunca
    // debe hacer fallar la cancelación del turno.
    releaseScheduleIfUnclaimed(updated.getSchedule(), updated.getId());

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
          "Cancelado por el paciente desde su portal");
    }

    return updatedDTO;
  }

  @Transactional
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

    // Design decision: reassignment must stay within the SAME service
    // (specialty) — a cardiology turn can never land on a dermatology slot,
    // since the patient booked for a kind of care, not a specific slot.
    // Changing DOCTOR within the same service is deliberately ALLOWED: the
    // most common real reason to reassign a turn is that the originally
    // assigned doctor became unavailable, so forcing the same doctor here
    // would make reassignment useless for exactly the case it exists to fix.
    Long oldServiceId = (oldSchedule != null && oldSchedule.getService() != null)
        ? oldSchedule.getService().getId()
        : null;
    Long newServiceId = newSchedule.getService() != null ? newSchedule.getService().getId() : null;

    if (oldServiceId == null || !oldServiceId.equals(newServiceId)) {
      throw new RuntimeException("No se puede reasignar el turno a un horario de un servicio distinto al original");
    }

    Long currentTurnsCount = turnRepository.countTurnsByServiceAndDate(
        newSchedule.getService().getId(),
        newSchedule.getDate());
    int nextOrder = (currentTurnsCount != null ? currentTurnsCount.intValue() : 0) + 1;

    try {
      operatorRepository.findById(UUID.fromString(staffAuthName)).ifPresent(turn::setOperator);
    } catch (Exception e) {
      // Ignorar si no es UUID de operador
    }

    // Ocupa el nuevo horario ANTES de tocar el turno: si alguien más lo ganó
    // en la misma fracción de segundo (ver occupySchedule), esto lanza aquí y
    // el turno original queda completamente intacto — nada se guarda todavía.
    occupySchedule(newSchedule);

    turn.setSchedule(newSchedule);
    turn.setOrder(nextOrder);
    turn.setStatus(TurnStatus.TURN_PENDING);

    Turn updated = turnRepository.save(turn);

    // Libera el horario anterior — solo si nadie más lo reclama y no fue
    // marcado como no disponible por un administrador (releaseScheduleIfUnclaimed).
    releaseScheduleIfUnclaimed(oldSchedule, updated.getId());

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

  @Transactional
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
    releaseScheduleIfUnclaimed(updated.getSchedule(), updated.getId());

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
  /**
   * Conteos del dia para las tarjetas del panel de turnos: por sede y por
   * servicio, con el total y cuantos siguen en TURN_PENDING.
   *
   * El agrupado lo hace la base (GROUP BY + COUNT). Traerse los turnos del dia
   * para contarlos en memoria seria pedir ~200 filas completas, con paciente y
   * cupo, para pintar dos numeros en una tarjeta.
   *
   * La reduccion a (total, pending) si es en memoria, y esta bien: son a lo
   * sumo cinco filas por sede — una por estado — y hacerlo en JPQL exigiria una
   * segunda consulta o un CASE que no aporta nada.
   */
  public TurnDailyCountsDTO getDailyCounts(LocalDate date) {
    return TurnDailyCountsDTO.builder()
        .date(date)
        .byStablishment(reduceStatusRows(turnRepository.countByStablishmentAndStatusInRange(date, date)))
        .byService(reduceStatusRows(turnRepository.countByServiceAndStatusForDate(date)))
        .build();
  }

  /**
   * Colapsa las filas (id, estado, cantidad) en una por id con total y
   * pendientes.
   *
   * `pending` arranca en 0 y no en null: un id que no tiene ninguna fila
   * TURN_PENDING igual informa 0, para que la tarjeta pinte el numero sin
   * tener que distinguir "cero" de "no hay dato".
   */
  private List<TurnDailyCountsDTO.ScopeCount> reduceStatusRows(List<LongStatusCountRow> rows) {
    Map<Long, long[]> acumulado = new LinkedHashMap<>();

    for (LongStatusCountRow row : rows) {
      if (row.getId() == null) {
        continue;
      }
      long cantidad = row.getTotal() == null ? 0L : row.getTotal();
      long[] par = acumulado.computeIfAbsent(row.getId(), key -> new long[] { 0L, 0L });
      par[0] += cantidad;
      if (row.getStatus() == TurnStatus.TURN_PENDING) {
        par[1] += cantidad;
      }
    }

    return acumulado.entrySet().stream()
        .map(entry -> TurnDailyCountsDTO.ScopeCount.builder()
            .id(entry.getKey())
            .total(entry.getValue()[0])
            .pending(entry.getValue()[1])
            .build())
        .toList();
  }

  private TurnDTO mapToDTO(Turn entity) {
    PatientDTO patientDTO = null;
    if (entity.getPatient() != null) {
      patientDTO = PatientDTO.builder()
          // Password stays hidden — never set here. uuid IS now exposed
          // (unlike before): staff needs it to book a follow-up turn for the
          // same patient via POST /api/turns/staff, which requires
          // patient.uuid in the request body. A patient reading their own
          // turn already knows their own uuid — it is their JWT subject.
          .uuid(entity.getPatient().getUuid())
          .email(entity.getPatient().getEmail())
          .firstName(entity.getPatient().getFirstName())
          .lastName(entity.getPatient().getLastName())
          .ci(entity.getPatient().getCi())
          .phone(entity.getPatient().getPhone())
          .build();
    }

    ScheduleDTO scheduleDTO = null;
    if (entity.getSchedule() != null) {
      DoctorDTO doctorDTO = null;
      if (entity.getSchedule().getDoctor() != null) {
        doctorDTO = DoctorDTO.builder()
            // uuid added: the client needs it to identify/link the doctor
            // (e.g. show "same doctor" on reassignment), consistent with
            // servicio/stablishment now also carrying their ids below.
            .uuid(entity.getSchedule().getDoctor().getUuid())
            .firstName(entity.getSchedule().getDoctor().getFirstName())
            .lastName(entity.getSchedule().getDoctor().getLastName())
            .speciality(entity.getSchedule().getDoctor().getSpeciality())
            .build();
      }

      ServicioDTO servicioDTO = null;
      if (entity.getSchedule().getService() != null) {
        servicioDTO = ServicioDTO.builder()
            // id added: the Angular reassign picker reads
            // turn.schedule.service.id to scope which schedules it offers.
            // Without it, the filter silently dropped and showed every
            // service's slots (see reassignTurn's same-service guard below).
            .id(entity.getSchedule().getService().getId())
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
                  // id added: a legitimate client need (e.g. filtering or
                  // linking back to "all turns at this establishment"),
                  // mirroring the same gap fixed on servicio/doctor above.
                  .id(entity.getSchedule().getStablishment().getId())
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

    // El prefijo sale del servicio del cupo. Un turno sin cupo o sin servicio
    // (no deberia pasar, pero el esquema lo permite) cae al numero pelado en
    // vez de romper el mapeo entero.
    String prefijoServicio = entity.getSchedule() != null && entity.getSchedule().getService() != null
        ? entity.getSchedule().getService().getPrefix()
        : null;

    // Solo id, código y etiqueta: el ConsultorioDTO completo arrastra su
    // StablishmentDTO, y la sede ya viaja en `schedule`. Repetirla haría que
    // dos ramas del mismo JSON pudieran contradecirse.
    ConsultorioDTO consultorioDTO = entity.getConsultorio() == null ? null
        : ConsultorioDTO.builder()
            .id(entity.getConsultorio().getId())
            .code(entity.getConsultorio().getCode())
            .label(entity.getConsultorio().getLabel())
            .build();

    return TurnDTO.builder()
        .id(entity.getId())
        .order(entity.getOrder())
        .ticket(com.devluis.utils.Ticket.format(prefijoServicio, entity.getOrder()))
        .consultorio(consultorioDTO)
        .status(entity.getStatus())
        .createdAt(entity.getCreatedAt())
        .finishedAt(entity.getFinishedAt())
        .cancelledAt(entity.getCancelledAt())
        .patient(patientDTO)
        .schedule(scheduleDTO)
        .operator(operatorDTO)
        .build();
  }

  /**
   * Ocupa un horario de forma atómica y traduce una pérdida de la carrera de
   * concurrencia en un mensaje claro en español.
   *
   * Mecanismo elegido: bloqueo optimista via {@code @Version} en
   * {@link Schedule} (ver esa entidad). {@code saveAndFlush} (en lugar de
   * {@code save}) es obligatorio aquí: dentro de un método
   * {@code @Transactional}, un {@code save()} normal solo deja el cambio
   * pendiente en el contexto de persistencia — el UPDATE real (y por lo
   * tanto la comprobación de versión) no se ejecuta hasta el commit, momento
   * en el que ya no es posible atraparlo con un try/catch dentro de este
   * método. {@code saveAndFlush} fuerza el UPDATE ... WHERE id = ? AND
   * version = ? de inmediato, así la excepción de bloqueo optimista ocurre,
   * de forma síncrona, exactamente aquí.
   */
  private void occupySchedule(Schedule schedule) {
    schedule.setStatus(ScheduleStatus.STATUS_OCCUPIED);
    try {
      scheduleRepository.saveAndFlush(schedule);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new RuntimeException(
          "Ese horario acaba de ser reservado por otra persona. Por favor, selecciona otro horario disponible.");
    }
  }

  /**
   * Libera un horario a STATUS_FREE tras un cancelTurn/reassignTurn — pero
   * SOLO si:
   * 1. Está actualmente STATUS_OCCUPIED. Un horario STATUS_UNAVAILABLE
   * (bloqueado manualmente por un administrador) nunca debe volver a
   * FREE solo porque un turno sobre él fue cancelado.
   * 2. Ningún OTRO turno todavía activo (distinto de {@code turnIdToExclude})
   * sigue apuntando a este horario.
   *
   * El punto 2 importa por dos razones: (a) en el estado estable, una vez que
   * toda reserva pasa por {@link #occupySchedule}, nunca debería haber más de
   * un turno activo por horario — pero (b) los datos creados ANTES de este
   * arreglo pueden violar esa invariante (es exactamente el bug de "un mismo
   * horario reservado sin límite" que este cambio corrige), y persisten hasta
   * que se corra la reconciliación SQL de una sola vez (ver reporte de apply).
   * Liberar el horario mientras otro turno activo lo sigue reclamando volvería
   * a ofrecer, por tercera vez, un horario que ya está doblemente reservado.
   *
   * Se excluye {@code turnIdToExclude} explícitamente por id (en lugar de
   * confiar en que su propio cambio de estado a CANCELADO ya sea visible para
   * esta consulta) para que la comprobación sea correcta sin depender del
   * orden de flush de Hibernate.
   *
   * Esto es un efecto secundario best-effort: cancelar o reasignar un turno
   * YA se guardó exitosamente antes de llegar aquí, así que una falla al
   * liberar (p. ej. una colisión de bloqueo optimista contra un administrador
   * marcando el horario como no disponible en el mismo instante) nunca debe
   * hacer fallar la operación completa — solo se registra y el horario queda,
   * en el peor caso, ocupado por más tiempo del necesario (falla en modo
   * seguro: nunca permite un doble booking, en el peor caso solo posterga la
   * liberación).
   */
  private void releaseScheduleIfUnclaimed(Schedule schedule, Long turnIdToExclude) {
    if (schedule == null || schedule.getStatus() != ScheduleStatus.STATUS_OCCUPIED) {
      return;
    }

    boolean stillClaimedByAnotherActiveTurn = turnRepository.existsByScheduleIdAndStatusNotAndIdNot(
        schedule.getId(), TurnStatus.TURN_CANCELLED, turnIdToExclude);
    if (stillClaimedByAnotherActiveTurn) {
      return;
    }

    schedule.setStatus(ScheduleStatus.STATUS_FREE);
    try {
      scheduleRepository.saveAndFlush(schedule);
    } catch (ObjectOptimisticLockingFailureException e) {
      log.warn("No se pudo liberar el horario {} tras una cancelación/reasignación del turno {}: {}",
          schedule.getId(), turnIdToExclude, e.getMessage());
    }
  }

  // Método auxiliar para mapear de Entidad a un payload anónimo, sin datos
  // del paciente, para el canal de broadcast por establecimiento.
  private TurnBoardDTO mapToBoardDTO(Turn entity) {
    Schedule schedule = entity.getSchedule();

    String serviceName = null;
    String doctorName = null;
    String stablishmentName = null;
    java.time.LocalTime hour = null;

    if (schedule != null) {
      hour = schedule.getHour();

      if (schedule.getService() != null) {
        serviceName = schedule.getService().getName();
      }

      if (schedule.getDoctor() != null) {
        doctorName = schedule.getDoctor().getFirstName() + " " + schedule.getDoctor().getLastName();
      }

      if (schedule.getStablishment() != null) {
        stablishmentName = schedule.getStablishment().getName();
      }
    }

    String prefix = schedule != null && schedule.getService() != null
        ? schedule.getService().getPrefix()
        : null;

    // El consultorio EFECTIVO del llamado sale del turno, no del cupo: el
    // operador pudo cambiarlo porque el medico se mudo hoy.
    com.devluis.entity.Consultorio consultorio = entity.getConsultorio();

    return TurnBoardDTO.builder()
        .id(entity.getId())
        .order(entity.getOrder())
        .status(entity.getStatus())
        .hour(hour)
        .serviceName(serviceName)
        .doctorName(doctorName)
        .stablishmentName(stablishmentName)
        .ticket(com.devluis.utils.Ticket.format(prefix, entity.getOrder()))
        .roomCode(consultorio != null ? consultorio.getCode() : null)
        .roomLabel(consultorio != null ? consultorio.getLabel() : null)
        .calledAt(entity.getCalledAt())
        .build();
  }

  /**
   * Sin id explicito devuelve el consultorio del cupo. Con id, valida que sea
   * de la MISMA sede del cupo: si no, la pantalla mandaria al paciente a una
   * puerta que no existe en ese edificio.
   */
  private com.devluis.entity.Consultorio resolveConsultorioForCall(Turn turn, Long consultorioId) {
    Schedule schedule = turn.getSchedule();

    if (consultorioId == null) {
      return schedule != null ? schedule.getConsultorio() : null;
    }

    com.devluis.entity.Consultorio consultorio = consultorioRepository.findById(consultorioId)
        .orElseThrow(() -> new RuntimeException("Consultorio no encontrado"));

    if (schedule != null && schedule.getStablishment() != null) {
      Long sedeDelCupo = schedule.getStablishment().getId();
      if (consultorio.getStablishment() == null
          || !consultorio.getStablishment().getId().equals(sedeDelCupo)) {
        throw new RuntimeException(
            "El consultorio seleccionado no pertenece al establecimiento de este turno");
      }
    }

    return consultorio;
  }

  private void broadcastTurnUpdate(Turn turn, TurnDTO turnDTO) {
    // Canal anónimo por establecimiento (pantallas de sala de espera +
    // señal de "algo cambió" para que el panel admin haga su propio refetch
    // autorizado). WebSocketConfig usa enableSimpleBroker, que NO autoriza
    // suscripciones: cualquier cliente autenticado (incluido cualquier otro
    // paciente) puede suscribirse a cualquier /topic/**. Por eso este canal
    // JAMÁS debe llevar un TurnDTO completo — solo TurnBoardDTO, que no tiene
    // ningún campo que identifique a un paciente.
    if (turn.getSchedule() != null && turn.getSchedule().getStablishment() != null
        && turn.getSchedule().getDate() != null) {
      String topic = "/topic/stablishment/" + turn.getSchedule().getStablishment().getId() + "/"
          + turn.getSchedule().getDate();
      messagingTemplate.convertAndSend(topic, mapToBoardDTO(turn));
    }

    // Canal por doctor, servicio y fecha usando autenticación (detalle
    // completo — solo lo recibe el doctor dueño del turno).
    if (turn.getSchedule() != null && turn.getSchedule().getDoctor() != null && turn.getSchedule().getService() != null
        && turn.getSchedule().getDate() != null) {
      String doctorUuid = turn.getSchedule().getDoctor().getUuid().toString();
      // El cliente se suscribirá a: /user/topic/service/{serviceId}/{date}
      String destination = "/topic/service/" + turn.getSchedule().getService().getId() + "/"
          + turn.getSchedule().getDate();
      messagingTemplate.convertAndSendToUser(doctorUuid, destination, turnDTO);
    }

    // Canal por paciente (detalle completo, pero únicamente de SU PROPIO
    // turno). Mismo mecanismo ya probado arriba para el doctor: el cliente
    // se suscribirá a /user/topic/turns.
    if (turn.getPatient() != null && turn.getPatient().getUuid() != null) {
      String patientUuid = turn.getPatient().getUuid().toString();
      messagingTemplate.convertAndSendToUser(patientUuid, "/topic/turns", turnDTO);
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
          doctorFullName);
    } catch (Exception e) {
      // Log del error pero no interrumpimos la creación del turno
      System.err.println("Error al enviar correo de notificación: " + e.getMessage());
    }
  }

  @Scheduled(cron = "0 0/5 * * * *")
  @Transactional
  public void checkUpcomingTurnsAndNotify() {
    LocalDate tomorrow = LocalDate.now().plusDays(1);
    List<Turn> upcomingTurns = turnRepository.findUpcomingPendingWithoutReminder(
        com.devluis.types.TurnStatus.TURN_PENDING, tomorrow);

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime limit = now.plusHours(24);

    for (Turn turn : upcomingTurns) {
      if (turn.getSchedule() != null && turn.getPatient() != null && turn.getPatient().getEmail() != null) {
        LocalDateTime turnStart = LocalDateTime.of(turn.getSchedule().getDate(), turn.getSchedule().getHour());

        // Verifica que el turno está entre 'ahora' y 'dentro de 24 horas'
        if (turnStart.isAfter(now) && !turnStart.isAfter(limit)) {
          String serviceName = turn.getSchedule().getService() != null ? turn.getSchedule().getService().getName()
              : "N/A";
          String date = turn.getSchedule().getDate() != null ? turn.getSchedule().getDate().toString() : "N/A";
          String hour = turn.getSchedule().getHour() != null ? turn.getSchedule().getHour().toString() : "N/A";
          String stablishmentName = turn.getSchedule().getStablishment() != null
              ? turn.getSchedule().getStablishment().getName()
              : "N/A";
          String doctorFullName = turn.getSchedule().getDoctor() != null
              ? turn.getSchedule().getDoctor().getFirstName() + " " + turn.getSchedule().getDoctor().getLastName()
              : "N/A";

          mailService.sendUpcomingTurnReminderEmail(
              turn.getPatient().getEmail(),
              turn.getPatient().getFirstName(),
              turn.getPatient().getLastName(),
              turn.getOrder(),
              serviceName,
              date,
              hour,
              stablishmentName,
              doctorFullName);

          turn.setReminderSent(true);
          turnRepository.save(turn);
        }
      }
    }
  }
}
