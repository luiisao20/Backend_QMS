package com.devluis.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.CoveragePlan;

public interface CoveragePlanRepository extends JpaRepository<CoveragePlan, Long> {

  Page<CoveragePlan> findByInsurerId(Long insurerId, Pageable pageable);

  // Delete guard for InsurerService — mirrors
  // HolidayRepository#existsByReasonId. UNVERIFIED AGAINST A REAL DATABASE —
  // see apply report.
  boolean existsByInsurerId(Long insurerId);
}
