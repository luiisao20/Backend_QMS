package com.devluis.dto;

import java.time.LocalTime;

import com.devluis.types.TurnStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal, anonymous payload for the establishment-wide waiting-room
 * broadcast channel ({@code /topic/stablishment/{stablishmentId}/{date}}).
 *
 * {@code WebSocketConfig} uses {@code enableSimpleBroker}, which performs NO
 * authorization on subscriptions — any authenticated client (including any
 * other patient) can subscribe to any {@code /topic/**} destination. This DTO
 * must therefore never carry a field that identifies a patient (uuid, name,
 * ci, email, phone). It carries just enough to render an anonymous
 * waiting-room display; the admin board treats receipt of any message on this
 * channel purely as a "something changed" signal and re-fetches full detail
 * over its own authorized REST endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnBoardDTO {
  private Long id;

  private Integer order;

  private TurnStatus status;

  private LocalTime hour;

  private String serviceName;

  private String doctorName;

  private String stablishmentName;

  /** "B-042": prefijo del servicio + order en 3 digitos. Ver Servicio.prefix. */
  private String ticket;

  /** "03" para la columna angosta. Null si el turno se llamo sin consultorio. */
  private String roomCode;

  /** "Consultorio 3" para el panel grande. Ver ConsultorioDTO sobre por que son dos. */
  private String roomLabel;

  /** Momento del llamado. La pantalla ordena el historial por este campo. */
  private java.time.OffsetDateTime calledAt;
}
