package com.devluis.controller;

import com.devluis.dto.OperatorDTO;
import com.devluis.services.OperatorService;
import com.devluis.services.MailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/operators")
@RequiredArgsConstructor
public class OperatorController {

  private final OperatorService operatorService;
  private final MailService mailService;

  @PostMapping("/register")
  public ResponseEntity<OperatorDTO> register(@Valid @RequestBody OperatorDTO operatorDTO) {
    OperatorDTO created = operatorService.register(operatorDTO);

    // Enviar correo de confirmación
    mailService.sendTestEmail(
        created.getEmail(),
        "Registro Exitoso - QMS",
        "Hola " + created.getFirstName() + " " + created.getLastName()
            + ",\n\nHas sido registrado exitosamente como Operador en el sistema QMS.");

    return new ResponseEntity<>(created, HttpStatus.CREATED);
  }

  @GetMapping
  public Page<OperatorDTO> getAll(
      @PageableDefault(size = 10) Pageable pageable) {
    return operatorService.getAll(pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<OperatorDTO> getOperator(@PathVariable UUID id) {
    return ResponseEntity.ok(operatorService.getOperatorById(id));
  }

  @PostMapping("/{id}/stablishments/{stablishmentId}")
  public ResponseEntity<OperatorDTO> assignToStablishment(
      @PathVariable UUID id,
      @PathVariable Long stablishmentId) {
    return ResponseEntity.ok(operatorService.assignToStablishment(id, stablishmentId));
  }

  @PutMapping("/{id}")
  public ResponseEntity<OperatorDTO> updateInfo(@PathVariable UUID id, @Valid @RequestBody OperatorDTO operatorDTO) {
    return ResponseEntity.ok(operatorService.updateOperator(id, operatorDTO));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteInfo(@PathVariable UUID id) {
    operatorService.deleteOperator(id);
    return ResponseEntity.noContent().build();
  }
}
