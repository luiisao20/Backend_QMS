package com.devluis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.Consultorio;

public interface ConsultorioRepository extends JpaRepository<Consultorio, Long> {

  List<Consultorio> findByStablishmentIdOrderByCodeAsc(Long stablishmentId);

  boolean existsByStablishmentIdAndCode(Long stablishmentId, String code);

  /** Para el update: el propio consultorio no cuenta como duplicado de sí mismo. */
  boolean existsByStablishmentIdAndCodeAndIdNot(Long stablishmentId, String code, Long id);
}
