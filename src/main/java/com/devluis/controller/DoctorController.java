package com.devluis.controller;

import com.devluis.dto.DoctorDTO;
import com.devluis.services.DoctorService;
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
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
public class DoctorController {

  private final DoctorService doctorService;
  private final MailService mailService;

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

  @GetMapping
  public Page<DoctorDTO> getAll(
      @PageableDefault(size = 10) Pageable pageable) {
    return doctorService.getAll(pageable);
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

    @PutMapping("/{id}")
  public ResponseEntity<DoctorDTO> updateInfo(@PathVariable UUID id, @Valid @RequestBody DoctorDTO doctorDTO) {
    return ResponseEntity.ok(doctorService.updateDoctor(id, doctorDTO));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteInfo(@PathVariable UUID id) {
    doctorService.deleteDoctor(id);
    return ResponseEntity.noContent().build();
  }
}
