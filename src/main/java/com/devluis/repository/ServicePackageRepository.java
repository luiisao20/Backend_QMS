package com.devluis.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.ServicePackage;

// UNVERIFIED against a real database — no DATABASE_URL is configured in
// this environment. See apply report.
public interface ServicePackageRepository extends JpaRepository<ServicePackage, Long> {

  Page<ServicePackage> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
