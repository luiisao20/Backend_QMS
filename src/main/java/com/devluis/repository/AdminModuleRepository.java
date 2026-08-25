package com.devluis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.AdminModule;

// No custom query methods on purpose: the catalog is small and fixed (12
// rows, see AdminModuleService.SEED), so AdminModuleService filters the
// result of the inherited findAll() in memory instead of adding a derived
// query method. Same "zero new JPQL surface" reasoning as BrandingRepository.
public interface AdminModuleRepository extends JpaRepository<AdminModule, Long> {
}
