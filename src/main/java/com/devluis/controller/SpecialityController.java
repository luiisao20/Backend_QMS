package com.devluis.controller;

import com.devluis.dto.SpecialityDTO;
import com.devluis.services.SpecialityService;
import jakarta.validation.Valid;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Catálogo de especialidades — la pantalla Admin → Especialidades del panel.
 *
 * `/active` existe además del listado paginado porque son dos consumidores
 * distintos: la tabla del panel quiere páginas y ver también las desactivadas,
 * un desplegable quiere la lista entera y solo las vigentes. Paginar un
 * desplegable obliga al cliente a recorrer páginas para armar un `<select>`.
 */
@RestController
@RequestMapping("/api/specialities")
@Data
public class SpecialityController {

  private final SpecialityService specialityService;

  @PostMapping
  public ResponseEntity<SpecialityDTO> create(@Valid @RequestBody SpecialityDTO dto) {
    return new ResponseEntity<>(specialityService.create(dto), HttpStatus.CREATED);
  }

  @GetMapping
  public Page<SpecialityDTO> getAll(
      @PageableDefault(size = 10) Pageable pageable) {
    return specialityService.getAll(pageable);
  }

  @GetMapping("/active")
  public ResponseEntity<List<SpecialityDTO>> getActive() {
    return ResponseEntity.ok(specialityService.getActive());
  }

  @GetMapping("/{id}")
  public ResponseEntity<SpecialityDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(specialityService.getById(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<SpecialityDTO> update(@PathVariable Long id, @Valid @RequestBody SpecialityDTO dto) {
    return ResponseEntity.ok(specialityService.update(id, dto));
  }

  /** Desactiva, no borra — ver `SpecialityService.delete`. */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    specialityService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
