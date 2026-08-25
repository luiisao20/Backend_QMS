package com.devluis.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.entity.Promotion;

// All queries below are UNVERIFIED against a real database — no DATABASE_URL
// is configured in this environment. See apply report.
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

  Page<Promotion> findByServicioId(Long servicioId, Pageable pageable);

  // Resolves "the" currently active promotion for a service on a given date.
  // Safe to assume at most one row matches: PromotionService rejects any
  // create/update whose date range would overlap another Promotion for the
  // same service, so the database can never contain two simultaneously
  // active promotions for one Servicio.
  Optional<Promotion> findFirstByServicioIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
      Long servicioId, LocalDate onOrAfterStart, LocalDate onOrBeforeEnd);

  // Overlap guard backing PromotionService's create/update rejection.
  // Interval overlap test: two ranges [s1,e1] and [s2,e2] overlap iff
  // s1 <= e2 AND s2 <= e1. excludeId is null on create (nothing to exclude)
  // and the promotion's own id on update (mirrors
  // PatientCoverageRepository#findByPatientUuidAndActiveTrueAndIdNot's
  // "exclude myself" idiom).
  @Query("SELECT COUNT(p) > 0 FROM Promotion p WHERE p.servicio.id = :servicioId "
      + "AND (:excludeId IS NULL OR p.id <> :excludeId) "
      + "AND p.startDate <= :endDate AND p.endDate >= :startDate")
  boolean existsOverlapping(
      @Param("servicioId") Long servicioId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("excludeId") Long excludeId);
}
