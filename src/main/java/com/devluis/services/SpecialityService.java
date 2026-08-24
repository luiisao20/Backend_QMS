package com.devluis.services;

import com.devluis.dto.SpecialityDTO;
import com.devluis.entity.Speciality;
import com.devluis.repository.SpecialityRepository;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Data
public class SpecialityService {

  private final SpecialityRepository specialityRepository;

  public SpecialityDTO create(SpecialityDTO dto) {
    String name = dto.getName().trim();
    assertNameIsFree(name, null);

    Speciality speciality = Speciality.builder()
        .name(name)
        .description(dto.getDescription())
        .active(dto.getActive() == null || dto.getActive())
        .build();

    return mapToDTO(specialityRepository.save(speciality), 0L);
  }

  public Page<SpecialityDTO> getAll(Pageable pageable) {
    Map<Long, Long> counts = doctorCounts();
    return specialityRepository.findAll(pageable)
        .map(entity -> mapToDTO(entity, counts.getOrDefault(entity.getId(), 0L)));
  }

  /** Para los desplegables: solo las activas, ordenadas, sin paginar. */
  public List<SpecialityDTO> getActive() {
    Map<Long, Long> counts = doctorCounts();
    return specialityRepository.findByActiveTrueOrderByNameAsc().stream()
        .map(entity -> mapToDTO(entity, counts.getOrDefault(entity.getId(), 0L)))
        .collect(Collectors.toList());
  }

  public SpecialityDTO getById(Long id) {
    Speciality speciality = findOrThrow(id);
    return mapToDTO(speciality, doctorCounts().getOrDefault(id, 0L));
  }

  public SpecialityDTO update(Long id, SpecialityDTO dto) {
    Speciality speciality = findOrThrow(id);
    String name = dto.getName().trim();

    // El duplicado se valida solo si el nombre cambió: sin esa condición,
    // guardar una especialidad sin tocarle el nombre falla contra sí misma.
    if (!speciality.getName().equalsIgnoreCase(name)) {
      assertNameIsFree(name, id);
    }

    speciality.setName(name);
    speciality.setDescription(dto.getDescription());
    if (dto.getActive() != null) {
      speciality.setActive(dto.getActive());
    }

    Speciality saved = specialityRepository.save(speciality);
    return mapToDTO(saved, doctorCounts().getOrDefault(id, 0L));
  }

  /**
   * Borrado LÓGICO, y no es una preferencia: hay doctores apuntando a esta fila y
   * su especialidad tiene que seguir siendo legible. Un borrado físico deja
   * `speciality_id` colgado o hace fallar la FK, que es exactamente la clase de
   * botón de borrar que este proyecto ya tiene marcado como problema.
   */
  public void delete(Long id) {
    Speciality speciality = findOrThrow(id);
    speciality.setActive(false);
    specialityRepository.save(speciality);
  }

  /** La entidad, para que `DoctorService` pueda resolver un `specialityId`. */
  public Speciality getEntity(Long id) {
    return findOrThrow(id);
  }

  private void assertNameIsFree(String name, Long ignoreId) {
    specialityRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
      if (ignoreId == null || !existing.getId().equals(ignoreId)) {
        throw new RuntimeException("Ya existe una especialidad con ese nombre");
      }
    });
  }

  private Map<Long, Long> doctorCounts() {
    Map<Long, Long> counts = new HashMap<>();
    specialityRepository.countDoctorsBySpeciality()
        .forEach(row -> counts.put(row.getSpecialityId(), row.getTotal()));
    return counts;
  }

  private Speciality findOrThrow(Long id) {
    return specialityRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Especialidad no encontrada"));
  }

  private SpecialityDTO mapToDTO(Speciality entity, Long doctorCount) {
    return SpecialityDTO.builder()
        .id(entity.getId())
        .name(entity.getName())
        .description(entity.getDescription())
        .active(entity.getActive())
        .doctorCount(doctorCount)
        .createdAt(entity.getCreatedAt())
        .build();
  }
}
