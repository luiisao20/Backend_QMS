package com.devluis.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.devluis.dto.ConsultorioDTO;
import com.devluis.services.ConsultorioService;

import jakarta.validation.Valid;
import lombok.Data;

/**
 * CRUD de consultorios de una sede.
 *
 * Las escrituras estan cerradas a ROLE_ADMIN en GlobalConfig, no con
 * @PreAuthorize, siguiendo lo que ya hace ScheduleTemplateController. Ese es el
 * requisito del negocio: el consultorio lo asigna un administrador, el medico
 * no puede asignarse el suyo.
 *
 * La lectura queda para cualquier usuario autenticado porque el operador
 * necesita la lista al llamar un turno, y el operador no es admin.
 *
 * OJO: GlobalConfig cierra la cadena con anyRequest().authenticated(), asi que
 * sin los matchers de mas abajo estos endpoints quedarian abiertos a cualquier
 * usuario logueado, incluido un paciente. Los matchers no son opcionales.
 */
@RestController
@RequestMapping("/api/consultorios")
@Data
public class ConsultorioController {

  private final ConsultorioService consultorioService;

  /** Listado por sede. No existe un "listar todos": un consultorio sin su sede no significa nada. */
  @GetMapping
  public ResponseEntity<List<ConsultorioDTO>> getByStablishment(@RequestParam Long stablishmentId) {
    return ResponseEntity.ok(consultorioService.getByStablishment(stablishmentId));
  }

  @PostMapping
  public ResponseEntity<ConsultorioDTO> create(@Valid @RequestBody ConsultorioDTO dto) {
    return new ResponseEntity<>(consultorioService.create(dto), HttpStatus.CREATED);
  }

  @PutMapping("/{id}")
  public ResponseEntity<ConsultorioDTO> update(@PathVariable Long id, @Valid @RequestBody ConsultorioDTO dto) {
    return ResponseEntity.ok(consultorioService.update(id, dto));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    consultorioService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
