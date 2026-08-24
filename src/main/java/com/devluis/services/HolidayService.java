package com.devluis.services;

import com.devluis.dto.HolidayDTO;
import com.devluis.dto.StablishmentDTO;
import com.devluis.entity.Holiday;
import com.devluis.entity.Stablishment;
import com.devluis.repository.HolidayRepository;
import com.devluis.repository.StablishmentRepository;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@Data
public class HolidayService {

  private final HolidayRepository holidayRepository;
  private final StablishmentRepository stablishmentRepository;

  public HolidayDTO create(HolidayDTO dto) {
    Stablishment stablishment = resolveStablishment(dto);
    assertNoDuplicate(dto.getDate(), stablishment, null);

    Holiday holiday = Holiday.builder()
        .date(dto.getDate())
        .name(dto.getName().trim())
        .stablishment(stablishment)
        .build();

    return mapToDTO(holidayRepository.save(holiday));
  }

  /**
   * Los feriados de un rango. Todo filtro es opcional.
   *
   * `stablishmentId` incluye SIEMPRE los nacionales: preguntar "qué feriados
   * afectan a esta sede" y recibir solo los que se cargaron para ella es la
   * respuesta equivocada — el 25 de diciembre le afecta igual.
   */
  public Page<HolidayDTO> search(LocalDate from, LocalDate to, Long stablishmentId, Pageable pageable) {
    return holidayRepository.search(from, to, stablishmentId, pageable).map(this::mapToDTO);
  }

  public HolidayDTO getById(Long id) {
    return mapToDTO(findOrThrow(id));
  }

  public HolidayDTO update(Long id, HolidayDTO dto) {
    Holiday holiday = findOrThrow(id);
    Stablishment stablishment = resolveStablishment(dto);
    assertNoDuplicate(dto.getDate(), stablishment, id);

    holiday.setDate(dto.getDate());
    holiday.setName(dto.getName().trim());
    holiday.setStablishment(stablishment);

    return mapToDTO(holidayRepository.save(holiday));
  }

  /**
   * Físico, y acá sí corresponde: un feriado no es referenciado por ninguna otra
   * fila. Es un calendario, no un catálogo con dependientes.
   */
  public void delete(Long id) {
    if (!holidayRepository.existsById(id)) {
      throw new RuntimeException("Feriado no encontrado");
    }
    holidayRepository.deleteById(id);
  }

  private Stablishment resolveStablishment(HolidayDTO dto) {
    if (dto.getStablishment() == null || dto.getStablishment().getId() == null) {
      return null; // Feriado nacional: aplica a todas las sedes.
    }
    return stablishmentRepository.findById(dto.getStablishment().getId())
        .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));
  }

  /**
   * Acá y no en un índice único de la tabla: `stablishment_id` es nullable y en
   * Postgres dos NULL no colisionan, así que un unique sobre (date,
   * stablishment_id) no impide cargar el mismo feriado nacional dos veces.
   */
  private void assertNoDuplicate(LocalDate date, Stablishment stablishment, Long ignoreId) {
    Long stablishmentId = stablishment == null ? null : stablishment.getId();
    List<Holiday> sameDay = holidayRepository.findSameDay(date, stablishmentId);

    boolean duplicated = sameDay.stream()
        .anyMatch(existing -> ignoreId == null || !existing.getId().equals(ignoreId));

    if (duplicated) {
      throw new RuntimeException(stablishmentId == null
          ? "Ya existe un feriado nacional en esa fecha"
          : "Ya existe un feriado en esa fecha para ese establecimiento");
    }
  }

  private Holiday findOrThrow(Long id) {
    return holidayRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Feriado no encontrado"));
  }

  private HolidayDTO mapToDTO(Holiday entity) {
    StablishmentDTO stablishmentDTO = null;
    if (entity.getStablishment() != null) {
      stablishmentDTO = StablishmentDTO.builder()
          .id(entity.getStablishment().getId())
          .name(entity.getStablishment().getName())
          .address(entity.getStablishment().getAddress())
          .build();
    }

    return HolidayDTO.builder()
        .id(entity.getId())
        .date(entity.getDate())
        .name(entity.getName())
        .stablishment(stablishmentDTO)
        .createdAt(entity.getCreatedAt())
        .build();
  }
}
