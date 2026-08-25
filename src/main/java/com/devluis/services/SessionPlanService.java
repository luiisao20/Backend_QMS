package com.devluis.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.devluis.dto.ServicioDTO;
import com.devluis.dto.SessionPlanDTO;
import com.devluis.entity.Servicio;
import com.devluis.entity.SessionPlan;
import com.devluis.repository.ServiceRepository;
import com.devluis.repository.SessionPlanRepository;
import com.devluis.utils.Money;

import lombok.Data;

@Service
@Data
public class SessionPlanService {
  private final SessionPlanRepository sessionPlanRepository;
  private final ServiceRepository serviceRepository;

  public SessionPlanDTO create(SessionPlanDTO dto) {
    Servicio servicio = resolveServicio(dto);

    SessionPlan plan = SessionPlan.builder()
        .servicio(servicio)
        .name(dto.getName())
        .sessionCount(dto.getSessionCount())
        .price(dto.getPrice())
        .build();

    SessionPlan saved = sessionPlanRepository.save(plan);
    return mapToDTO(saved);
  }

  public Page<SessionPlanDTO> getAll(Long servicioId, Pageable pageable) {
    if (servicioId != null) {
      return sessionPlanRepository.findByServicioId(servicioId, pageable).map(this::mapToDTO);
    }
    return sessionPlanRepository.findAll(pageable).map(this::mapToDTO);
  }

  public Page<SessionPlanDTO> getAll(Pageable pageable) {
    return getAll(null, pageable);
  }

  public SessionPlanDTO getById(Long id) {
    return mapToDTO(findByIdOrThrow(id));
  }

  public SessionPlanDTO update(Long id, SessionPlanDTO dto) {
    SessionPlan plan = findByIdOrThrow(id);
    Servicio servicio = resolveServicio(dto);

    plan.setServicio(servicio);
    plan.setName(dto.getName());
    plan.setSessionCount(dto.getSessionCount());
    plan.setPrice(dto.getPrice());

    SessionPlan updated = sessionPlanRepository.save(plan);
    return mapToDTO(updated);
  }

  // No dependents guard needed: session consumption is deliberately not
  // modelled (see SessionPlan's docblock), so nothing else in this codebase
  // references a SessionPlan row.
  public void delete(Long id) {
    if (!sessionPlanRepository.existsById(id)) {
      throw new RuntimeException("Plan de sesiones no encontrado");
    }
    sessionPlanRepository.deleteById(id);
  }

  private SessionPlan findByIdOrThrow(Long id) {
    return sessionPlanRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Plan de sesiones no encontrado"));
  }

  private Servicio resolveServicio(SessionPlanDTO dto) {
    return serviceRepository.findById(dto.getServicio().getId())
        .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));
  }

  private SessionPlanDTO mapToDTO(SessionPlan entity) {
    ServicioDTO servicioDTO = ServicioDTO.builder()
        .id(entity.getServicio().getId())
        .name(entity.getServicio().getName())
        .price(entity.getServicio().getPrice())
        .discount(entity.getServicio().getDiscount())
        .build();

    BigDecimal price = Money.of(entity.getPrice());
    BigDecimal pricePerSession = price.divide(
        BigDecimal.valueOf(entity.getSessionCount()), Money.SCALE, RoundingMode.HALF_UP);
    BigDecimal regularTotal = Money.netPrice(entity.getServicio())
        .multiply(BigDecimal.valueOf(entity.getSessionCount()));
    BigDecimal savings = regularTotal.subtract(price);

    return SessionPlanDTO.builder()
        .id(entity.getId())
        .servicio(servicioDTO)
        .name(entity.getName())
        .sessionCount(entity.getSessionCount())
        .price(price)
        .pricePerSession(pricePerSession)
        .regularTotal(regularTotal)
        .savings(savings)
        .createdAt(entity.getCreatedAt())
        .build();
  }
}
