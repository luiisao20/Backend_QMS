package com.devluis.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devluis.dto.WaitingRoomScreenDTO;
import com.devluis.services.SalaService;

import lombok.Data;

/**
 * Carga inicial de la pantalla de sala de espera.
 *
 * PUBLICO a proposito, con matcher explicito en GlobalConfig. Un televisor
 * colgado en un hall no tiene quien se loguee: no hay teclado, no hay sesion,
 * no hay nadie mirando cuando el token expira a las 3 de la manana.
 *
 * Que sea publico es seguro porque la respuesta no lleva NINGUN campo que
 * identifique a un paciente: numero de turno, consultorio, especialidad y hora
 * de llamado. Es exactamente lo que ya se grita en voz alta en la sala. Mismo
 * criterio que TurnBoardDTO en el canal anonimo del socket.
 *
 * Si alguien agrega un nombre de paciente a este DTO, lo publica en internet.
 */
@RestController
@RequestMapping("/api/sala")
@Data
public class SalaController {

  private final SalaService salaService;

  /** Acepta el id numerico o el nombre de la sede: ver SalaService#getScreen. */
  @GetMapping("/{sedeId}/pantalla")
  public ResponseEntity<WaitingRoomScreenDTO> getScreen(@PathVariable String sedeId) {
    return ResponseEntity.ok(salaService.getScreen(sedeId));
  }
}
