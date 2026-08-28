package com.devluis.dto;

import java.time.OffsetDateTime;

import com.devluis.types.TurnStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TurnDTO {
  private Long id;

  private Integer order;

  /**
   * "C-001": el numero que el paciente escucha y ve en la pantalla de sala.
   *
   * `order` solo no alcanza para mostrarlo en ningun lado. El orden se calcula
   * POR SERVICIO Y POR FECHA, asi que dos servicios tienen un turno #1 el mismo
   * dia; sin el prefijo del servicio el numero es ambiguo y dos pacientes
   * caminan al mismo llamado.
   *
   * Hasta que existio este campo, el ticket vivia SOLO en TurnBoardDTO, o sea
   * unicamente en el televisor: el operador que llamaba a alguien veia "#1" y
   * no podia decirle su numero, y la app del paciente no tenia como mostrarlo.
   *
   * Se formatea con utils/Ticket, nunca a mano en un cliente — ver su docblock.
   */
  private String ticket;

  private TurnStatus status;

  private OffsetDateTime createdAt;

  private OffsetDateTime finishedAt;

  private OffsetDateTime cancelledAt;

  private OperatorDTO operator;

  private PatientDTO patient;

  private ScheduleDTO schedule;

  /**
   * El consultorio EFECTIVO por el que salió el llamado.
   *
   * Es el del turno y no el del cupo: el cupo trae el que asignó un admin en la
   * plantilla, pero el operador puede cambiarlo al llamar porque el médico se
   * mudó de sala. El que le sirve a quien mira una lista es por dónde salió de
   * verdad.
   *
   * Null mientras nadie lo haya llamado, que es lo correcto: antes del llamado
   * no hay consultorio efectivo todavía.
   */
  private ConsultorioDTO consultorio;

}
