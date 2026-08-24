package com.devluis.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.entity.Patient;

public interface PatientRepository extends JpaRepository<Patient, UUID> {
  Optional<Patient> findByEmail(String email);

  Optional<Patient> findByCi(String ci);

  @Query("SELECT p FROM Patient p WHERE " +
      "(:ci IS NULL OR :ci = '' OR LOWER(p.ci) LIKE LOWER(CONCAT('%', :ci, '%'))) AND " +
      "(:name IS NULL OR :name = '' OR LOWER(CONCAT(p.firstName, ' ', p.lastName)) LIKE LOWER(CONCAT('%', :name, '%')) "
      +
      "OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', :name, '%')) " +
      "OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :name, '%')))")
  Page<Patient> findByFilters(@Param("name") String name, @Param("ci") String ci, Pageable pageable);
}
