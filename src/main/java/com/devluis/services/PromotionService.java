package com.devluis.services;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.devluis.dto.PromotionDTO;
import com.devluis.dto.ServicioDTO;
import com.devluis.entity.Promotion;
import com.devluis.entity.Servicio;
import com.devluis.repository.PromotionRepository;
import com.devluis.repository.ServiceRepository;
import com.devluis.types.DiscountType;

import lombok.Data;

@Service
@Data
public class PromotionService {
  private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

  private final PromotionRepository promotionRepository;
  private final ServiceRepository serviceRepository;

  public PromotionDTO create(PromotionDTO dto) {
    Servicio servicio = resolveServicio(dto);
    validateDateRange(dto);
    validatePercentageBounds(dto);
    rejectIfOverlapping(servicio.getId(), dto.getStartDate(), dto.getEndDate(), null);

    Promotion promotion = Promotion.builder()
        .servicio(servicio)
        .name(dto.getName())
        .discountType(dto.getDiscountType())
        .discountValue(dto.getDiscountValue())
        .startDate(dto.getStartDate())
        .endDate(dto.getEndDate())
        .build();

    Promotion saved = promotionRepository.save(promotion);
    return mapToDTO(saved);
  }

  public Page<PromotionDTO> getAll(Long servicioId, Pageable pageable) {
    if (servicioId != null) {
      return promotionRepository.findByServicioId(servicioId, pageable).map(this::mapToDTO);
    }
    return promotionRepository.findAll(pageable).map(this::mapToDTO);
  }

  public Page<PromotionDTO> getAll(Pageable pageable) {
    return getAll(null, pageable);
  }

  public PromotionDTO getById(Long id) {
    return mapToDTO(findByIdOrThrow(id));
  }

  public PromotionDTO update(Long id, PromotionDTO dto) {
    Promotion promotion = findByIdOrThrow(id);
    Servicio servicio = resolveServicio(dto);
    validateDateRange(dto);
    validatePercentageBounds(dto);
    rejectIfOverlapping(servicio.getId(), dto.getStartDate(), dto.getEndDate(), id);

    promotion.setServicio(servicio);
    promotion.setName(dto.getName());
    promotion.setDiscountType(dto.getDiscountType());
    promotion.setDiscountValue(dto.getDiscountValue());
    promotion.setStartDate(dto.getStartDate());
    promotion.setEndDate(dto.getEndDate());

    Promotion updated = promotionRepository.save(promotion);
    return mapToDTO(updated);
  }

  public void delete(Long id) {
    if (!promotionRepository.existsById(id)) {
      throw new RuntimeException("Promoción no encontrada");
    }
    promotionRepository.deleteById(id);
  }

  private Promotion findByIdOrThrow(Long id) {
    return promotionRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Promoción no encontrada"));
  }

  private Servicio resolveServicio(PromotionDTO dto) {
    return serviceRepository.findById(dto.getServicio().getId())
        .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));
  }

  private void validateDateRange(PromotionDTO dto) {
    if (dto.getEndDate().isBefore(dto.getStartDate())) {
      throw new RuntimeException("La fecha de fin no puede ser anterior a la fecha de inicio");
    }
  }

  private void validatePercentageBounds(PromotionDTO dto) {
    if (dto.getDiscountType() == DiscountType.PERCENTAGE
        && dto.getDiscountValue().compareTo(ONE_HUNDRED) > 0) {
      throw new RuntimeException("El porcentaje de descuento no puede superar 100");
    }
  }

  // Rejects the write outright instead of silently deactivating the older
  // promotion or letting both stack — see Promotion's docblock.
  private void rejectIfOverlapping(Long servicioId, LocalDate startDate, LocalDate endDate, Long excludeId) {
    if (promotionRepository.existsOverlapping(servicioId, startDate, endDate, excludeId)) {
      throw new RuntimeException(
          "Ya existe una promoción vigente para este servicio en ese rango de fechas. "
              + "Ajuste las fechas o finalice la promoción existente antes de crear esta.");
    }
  }

  private PromotionDTO mapToDTO(Promotion entity) {
    ServicioDTO servicioDTO = null;
    if (entity.getServicio() != null) {
      servicioDTO = ServicioDTO.builder()
          .id(entity.getServicio().getId())
          .name(entity.getServicio().getName())
          .price(entity.getServicio().getPrice())
          .discount(entity.getServicio().getDiscount())
          .build();
    }

    LocalDate today = LocalDate.now();
    boolean currentlyActive = !today.isBefore(entity.getStartDate()) && !today.isAfter(entity.getEndDate());

    return PromotionDTO.builder()
        .id(entity.getId())
        .servicio(servicioDTO)
        .name(entity.getName())
        .discountType(entity.getDiscountType())
        .discountValue(entity.getDiscountValue())
        .startDate(entity.getStartDate())
        .endDate(entity.getEndDate())
        .currentlyActive(currentlyActive)
        .createdAt(entity.getCreatedAt())
        .build();
  }
}
