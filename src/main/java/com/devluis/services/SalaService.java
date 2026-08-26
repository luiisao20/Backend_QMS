package com.devluis.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

  /** Filas que entran en la columna de llamados sin recortarse. */
  private static final int HISTORY_SIZE = 13;

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

    // Ya vienen del mas reciente al mas viejo por calledAt.
    List<Turn> called = turnRepository.findCalledForBoard(stablishmentId, LocalDate.now());

    Turn current = called.stream()
        .filter(t -> t.getStatus() == TurnStatus.TURN_IN_TREATMENT)
        .findFirst()
        .orElse(null);

    // history son los llamados ANTERIORES al actual. Excluir el actual por id y
    // no por posicion: si nadie esta en atencion, current es null y la lista
    // entera es historial.
    List<WaitingRoomScreenDTO.Call> history = new ArrayList<>();
    for (Turn turn : called) {
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

  /** Stablishment no modela ciudad aparte; lo mas cercano es la direccion. */
  private String locationOf(Stablishment stablishment) {
    if (stablishment.getAddress() == null || stablishment.getAddress().isBlank()) {
      return stablishment.getName();
    }
    return stablishment.getName() + " - " + stablishment.getAddress();
  }
}
