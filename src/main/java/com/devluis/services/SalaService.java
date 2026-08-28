package com.devluis.services;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.devluis.dto.WaitingRoomScreenDTO;
import com.devluis.entity.Branding;
import com.devluis.entity.Consultorio;
import com.devluis.entity.Schedule;
import com.devluis.entity.Stablishment;
import com.devluis.entity.Turn;
import com.devluis.repository.BrandingRepository;
import com.devluis.repository.StablishmentRepository;
import com.devluis.repository.TurnRepository;
import com.devluis.types.TurnStatus;
import com.devluis.utils.Ticket;

import lombok.Data;

/**
 * Arma el estado completo de una pantalla de sala.
 *
 * Existe porque el socket NO alcanza para pintar la pantalla: emite UN turno
 * por evento, asi que un televisor que se enciende a las 3 de la tarde no tiene
 * el historial de la manana. Este endpoint es la carga inicial; despues el
 * socket avisa "cambio algo" y la pantalla vuelve a pedir esto.
 *
 * Ese ida y vuelta es ademas la regla que ya estaba escrita en el docblock de
 * RealtimeService del cliente: "llego un mensaje a este topic -> re-fetch del
 * estado autoritativo por REST", nunca confiar en el payload del socket.
 */
@Service
@Data
public class SalaService {

  /** Filas que entran en la columna sin recortarse. */
  private static final int HISTORY_SIZE = 13;

  /**
   * Los unicos dos estados que ponen a alguien DENTRO de la sala.
   *
   * Un turno atendido ya se fue del edificio y uno cancelado no tiene nada que
   * esperar: ninguno de los dos le dice nada a la persona sentada mirando la
   * pantalla. TURN_PENDING queda afuera por lo contrario -- todavia no paso
   * por recepcion, asi que tampoco esta en la sala.
   *
   * TURN_IN_TREATMENT es ademas lo que necesita el que llega tarde: un llamado
   * que nadie finalizo se queda en ese estado, asi que puede leer la columna y
   * enterarse de que ya lo llamaron aunque su numero ya no este en el panel
   * grande.
   *
   * La regla vive aca y no dentro de la JPQL porque aca tiene test: el
   * proyecto no tiene base de datos en el entorno de pruebas. Ver
   * SalaServiceTest.
   */
  private static final Set<TurnStatus> BOARD_STATUSES =
      EnumSet.of(TurnStatus.TURN_IN_TREATMENT, TurnStatus.TURN_WAITNG);

  private final TurnRepository turnRepository;
  private final StablishmentRepository stablishmentRepository;
  private final BrandingRepository brandingRepository;

  /**
   * El identificador de la sede tal como viene en la URL del televisor.
   *
   * Acepta el id numerico o el NOMBRE de la sede, porque la pantalla se
   * configura una sola vez pegando una URL en un navegador que nadie va a
   * volver a tocar: "/sala/matriz" se lee y se escribe a mano sin errores,
   * "/sala/7" no.
   */
  public WaitingRoomScreenDTO getScreen(String sedeId) {
    Stablishment stablishment = resolveStablishment(sedeId);
    Long stablishmentId = stablishment.getId();

    Branding branding = brandingRepository.findAll().stream().findFirst().orElse(null);

    List<Turn> onSite = turnRepository.findBoardTurns(
        stablishmentId, LocalDate.now(), BOARD_STATUSES);

    // Llamados: del llamado mas reciente al mas viejo. Por calledAt y NO por
    // createdAt -- createdAt es cuando se reservo el turno, que puede ser de
    // hace una semana y no dice nada del orden en que se llamo a la gente hoy.
    List<Turn> called = onSite.stream()
        .filter(t -> t.getStatus() == TurnStatus.TURN_IN_TREATMENT)
        .sorted(Comparator.comparing(Turn::getCalledAt,
            Comparator.nullsLast(Comparator.<OffsetDateTime>reverseOrder())))
        .toList();

    // En espera: por la hora de su cita, que es el orden en que los van a
    // llamar. El que espera busca SU numero y cuenta cuantos tiene encima; con
    // cualquier otro orden esa cuenta no significa nada.
    List<Turn> waiting = onSite.stream()
        .filter(t -> t.getStatus() == TurnStatus.TURN_WAITNG)
        .sorted(Comparator.<Turn, LocalTime>comparing(SalaService::hourOf,
                Comparator.nullsLast(Comparator.<LocalTime>naturalOrder()))
            .thenComparing(Turn::getOrder,
                Comparator.nullsLast(Comparator.<Integer>naturalOrder())))
        .toList();

    Turn current = called.stream().findFirst().orElse(null);

    // La columna: primero el resto de los llamados, despues la cola. Excluir el
    // actual por id y no por posicion: si nadie esta en atencion, current es
    // null y no hay nada que saltear.
    //
    // El recorte de HISTORY_SIZE cae sobre la cola, no sobre los llamados, y
    // asi tiene que ser: perder un llamado es perder justo el dato por el que
    // el que llego tarde mira la pantalla.
    List<Turn> column = new ArrayList<>(called);
    column.addAll(waiting);

    List<WaitingRoomScreenDTO.Call> history = new ArrayList<>();
    for (Turn turn : column) {
      if (current != null && turn.getId().equals(current.getId())) {
        continue;
      }
      if (history.size() == HISTORY_SIZE) {
        break;
      }
      history.add(WaitingRoomScreenDTO.Call.builder()
          .ticket(ticketOf(turn))
          .room(roomCodeOf(turn))
          .calledAt(turn.getCalledAt())
          .build());
    }

    return WaitingRoomScreenDTO.builder()
        .site(WaitingRoomScreenDTO.Site.builder()
            .stablishmentId(stablishmentId)
            .brand(branding != null ? branding.getName() : null)
            .location(locationOf(stablishment))
            .build())
        .current(current == null ? null : toCurrent(current))
        .history(history)
        .ticker(branding != null ? branding.getWaitingRoomTicker() : null)
        .build();
  }

  private Stablishment resolveStablishment(String sedeId) {
    if (sedeId != null && sedeId.chars().allMatch(Character::isDigit)) {
      return stablishmentRepository.findById(Long.valueOf(sedeId))
          .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));
    }

    // Coincidencia por nombre, sin distinguir mayusculas ni espacios de mas.
    // Exacta a proposito: un "contains" haria que "norte" matchee tambien
    // "Norte 2" y la pantalla equivocada mostraria los turnos de otra sede.
    String target = sedeId == null ? "" : sedeId.trim();
    return stablishmentRepository.findAll().stream()
        .filter(st -> st.getName() != null && st.getName().trim().equalsIgnoreCase(target))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));
  }

  private WaitingRoomScreenDTO.CurrentCall toCurrent(Turn turn) {
    Consultorio consultorio = turn.getConsultorio();
    Schedule schedule = turn.getSchedule();

    return WaitingRoomScreenDTO.CurrentCall.builder()
        .ticket(ticketOf(turn))
        .room(consultorio != null ? consultorio.getCode() : null)
        .roomLabel(consultorio != null ? consultorio.getLabel() : null)
        .specialty(schedule != null && schedule.getService() != null
            ? schedule.getService().getName()
            : null)
        .calledAt(turn.getCalledAt())
        .build();
  }

  private String ticketOf(Turn turn) {
    Schedule schedule = turn.getSchedule();
    String prefix = schedule != null && schedule.getService() != null
        ? schedule.getService().getPrefix()
        : null;
    return Ticket.format(prefix, turn.getOrder());
  }

  private String roomCodeOf(Turn turn) {
    return turn.getConsultorio() != null ? turn.getConsultorio().getCode() : null;
  }

  /**
   * La hora de la cita, o null si el turno no tiene cupo cargado.
   *
   * Statica porque se usa como clave de orden y un Comparator no deberia
   * depender de la instancia del servicio.
   */
  private static LocalTime hourOf(Turn turn) {
    Schedule schedule = turn.getSchedule();
    return schedule == null ? null : schedule.getHour();
  }

  /** Stablishment no modela ciudad aparte; lo mas cercano es la direccion. */
  private String locationOf(Stablishment stablishment) {
    if (stablishment.getAddress() == null || stablishment.getAddress().isBlank()) {
      return stablishment.getName();
    }
    return stablishment.getName() + " - " + stablishment.getAddress();
  }
}
