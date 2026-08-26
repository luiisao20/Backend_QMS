package com.devluis.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.devluis.dto.ConsultorioDTO;
import com.devluis.dto.StablishmentDTO;
import com.devluis.entity.Consultorio;
import com.devluis.entity.Stablishment;
import com.devluis.repository.ConsultorioRepository;
import com.devluis.repository.ScheduleTemplateRepository;
import com.devluis.repository.StablishmentRepository;

import lombok.Data;

/**
 * CRUD de consultorios, siempre en el contexto de una sede.
 *
 * No hay un "listar todos los consultorios" a propósito: un consultorio suelto,
 * sin su sede, no significa nada — el mismo "03" existe en varias sedes. Todas
 * las lecturas entran por `getByStablishment`.
 */
@Service
@Data
public class ConsultorioService {

  private final ConsultorioRepository consultorioRepository;
  private final StablishmentRepository stablishmentRepository;
  private final ScheduleTemplateRepository scheduleTemplateRepository;

  public List<ConsultorioDTO> getByStablishment(Long stablishmentId) {
    return consultorioRepository.findByStablishmentIdOrderByCodeAsc(stablishmentId)
        .stream().map(this::mapToDTO).collect(Collectors.toList());
  }

  public ConsultorioDTO create(ConsultorioDTO dto) {
    Stablishment stablishment = resolveStablishment(dto);

    if (consultorioRepository.existsByStablishmentIdAndCode(stablishment.getId(), dto.getCode())) {
      throw new RuntimeException(
          "Ya existe un consultorio con el código '" + dto.getCode() + "' en este establecimiento");
    }

    Consultorio consultorio = Consultorio.builder()
        .code(dto.getCode())
        .label(dto.getLabel())
        .stablishment(stablishment)
        .active(dto.getActive() != null ? dto.getActive() : Boolean.TRUE)
        .build();

    return mapToDTO(consultorioRepository.save(consultorio));
  }

  public ConsultorioDTO update(Long id, ConsultorioDTO dto) {
    Consultorio consultorio = consultorioRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Consultorio no encontrado"));

    Stablishment stablishment = resolveStablishment(dto);

    if (consultorioRepository.existsByStablishmentIdAndCodeAndIdNot(
        stablishment.getId(), dto.getCode(), id)) {
      throw new RuntimeException(
          "Ya existe un consultorio con el código '" + dto.getCode() + "' en este establecimiento");
    }

    consultorio.setCode(dto.getCode());
    consultorio.setLabel(dto.getLabel());
    consultorio.setStablishment(stablishment);
    if (dto.getActive() != null) {
      consultorio.setActive(dto.getActive());
    }

    return mapToDTO(consultorioRepository.save(consultorio));
  }

  /**
   * Borrado real, sólo si nadie lo referencia. Un consultorio que ya está en una
   * plantilla se DESACTIVA (`active = false`), no se borra: si se fuera, la
   * plantilla quedaría apuntando al vacío y la pantalla de sala perdería el dato
   * del turno que se está llamando ahora mismo.
   */
  public void delete(Long id) {
    if (!consultorioRepository.existsById(id)) {
      throw new RuntimeException("Consultorio no encontrado");
    }

    if (scheduleTemplateRepository.existsByConsultorioId(id)) {
      throw new RuntimeException(
          "No se puede eliminar el consultorio porque está asignado a un horario de atención. "
              + "Desactívalo en lugar de eliminarlo.");
    }

    consultorioRepository.deleteById(id);
  }

  private Stablishment resolveStablishment(ConsultorioDTO dto) {
    if (dto.getStablishment() == null || dto.getStablishment().getId() == null) {
      throw new RuntimeException("El establecimiento es requerido");
    }
    return stablishmentRepository.findById(dto.getStablishment().getId())
        .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));
  }

  private ConsultorioDTO mapToDTO(Consultorio entity) {
    Stablishment stablishment = entity.getStablishment();

    return ConsultorioDTO.builder()
        .id(entity.getId())
        .code(entity.getCode())
        .label(entity.getLabel())
        .active(entity.getActive())
        .stablishment(stablishment == null ? null
            : StablishmentDTO.builder()
                .id(stablishment.getId())
                .name(stablishment.getName())
                .build())
        .build();
  }
}
