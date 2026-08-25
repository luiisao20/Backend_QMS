package com.devluis.controller;

import com.devluis.dto.DoctorDTO;
import com.devluis.services.DoctorService;
import com.devluis.services.MailService;
import com.devluis.types.ChangePasswordBody;
import com.devluis.utils.Helper;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
public class DoctorController {

  private final DoctorService doctorService;
  private final MailService mailService;
  private final com.devluis.services.InvoiceService invoiceService;

  @PostMapping("/register")
  public ResponseEntity<DoctorDTO> register(@Valid @RequestBody DoctorDTO doctorDTO) {
    DoctorDTO created = doctorService.register(doctorDTO);

    // Enviar correo de confirmación
    mailService.sendTestEmail(
        created.getEmail(),
        "Registro Exitoso - QMS",
        "Hola " + created.getFirstName() + " " + created.getLastName()
            + ",\n\nHas sido registrado exitosamente como Doctor en el sistema QMS.");

    return new ResponseEntity<>(created, HttpStatus.CREATED);
  }

  @GetMapping("/me/invoices")
  public ResponseEntity<Page<com.devluis.dto.InvoiceDTO>> getMyInvoices(
      @PageableDefault(size = 10) Pageable pageable, Authentication auth) {
    UUID doctorUuid = UUID.fromString(auth.getName());
    return ResponseEntity.ok(invoiceService.getForDoctor(doctorUuid, pageable));
  }

  @GetMapping
  public Page<DoctorDTO> getAll(
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String ci,
      @PageableDefault(size = 10) Pageable pageable) {
    return doctorService.getAll(name, ci, pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<DoctorDTO> getDoctor(@PathVariable UUID id) {
    return ResponseEntity.ok(doctorService.getDoctorById(id));
  }

  @PostMapping("/{id}/stablishments/{stablishmentId}")
  public ResponseEntity<DoctorDTO> assignToStablishment(
      @PathVariable UUID id,
      @PathVariable Long stablishmentId) {
    return ResponseEntity.ok(doctorService.assignToStablishment(id, stablishmentId));
  }

  @PostMapping("/{id}/services/{serviceId}")
  public ResponseEntity<DoctorDTO> assignToService(
      @PathVariable UUID id,
      @PathVariable Long serviceId) {
    return ResponseEntity.ok(doctorService.assignToService(id, serviceId));
  }

  @DeleteMapping("/{id}/stablishments/{stablishmentId}")
  public ResponseEntity<DoctorDTO> revokeStablishment(
      @PathVariable UUID id,
      @PathVariable Long stablishmentId) {
    return ResponseEntity.ok(doctorService.revokeStablishment(id, stablishmentId));
  }

  @DeleteMapping("/{id}/services/{serviceId}")
  public ResponseEntity<DoctorDTO> revokeService(
      @PathVariable UUID id,
      @PathVariable Long serviceId) {
    return ResponseEntity.ok(doctorService.revokeService(id, serviceId));
  }

  @PutMapping("/{id}")
  public ResponseEntity<DoctorDTO> updateInfo(@PathVariable UUID id, @Valid @RequestBody DoctorDTO doctorDTO) {
    return ResponseEntity.ok(doctorService.updateDoctor(id, doctorDTO));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteInfo(@PathVariable UUID id) {
    doctorService.deleteDoctor(id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/change-password")
  public ResponseEntity<?> changeMyPassword(
      @Valid @RequestBody ChangePasswordBody body,
      Authentication auth) {
    try {
      if (!body.getPassword().equals(body.getRepeatedPassword())) {
        return Helper.getResponseMessage("Las contraseñas no coinciden", HttpStatus.BAD_REQUEST);
      }
      UUID uuid = UUID.fromString(auth.getName());
      doctorService.updatePassword(uuid, body.getPassword());
      return ResponseEntity.ok(Map.of("Message", "Contraseña actualizada exitosamente"));
    } catch (RuntimeException e) {
      return Helper.getResponseMessage(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }
}
