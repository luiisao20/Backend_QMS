package com.devluis.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.entity.Doctor;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {
  Optional<Doctor> findByEmail(String email);
  Optional<Doctor> findByCi(String ci);

  @Query("SELECT d FROM Doctor d WHERE " +
         "(:ci IS NULL OR :ci = '' OR LOWER(d.ci) LIKE LOWER(CONCAT('%', :ci, '%'))) AND " +
         "(:name IS NULL OR :name = '' OR LOWER(CONCAT(d.firstName, ' ', d.lastName)) LIKE LOWER(CONCAT('%', :name, '%')) " +
         "OR LOWER(d.firstName) LIKE LOWER(CONCAT('%', :name, '%')) " +
         "OR LOWER(d.lastName) LIKE LOWER(CONCAT('%', :name, '%')))")
  Page<Doctor> findByFilters(@Param("name") String name, @Param("ci") String ci, Pageable pageable);

  @Query("SELECT d FROM Doctor d JOIN d.stablishments est WHERE est.id = :stablishmentId AND " +
         "(:ci IS NULL OR :ci = '' OR LOWER(d.ci) LIKE LOWER(CONCAT('%', :ci, '%'))) AND " +
         "(:name IS NULL OR :name = '' OR LOWER(CONCAT(d.firstName, ' ', d.lastName)) LIKE LOWER(CONCAT('%', :name, '%')) " +
         "OR LOWER(d.firstName) LIKE LOWER(CONCAT('%', :name, '%')) " +
         "OR LOWER(d.lastName) LIKE LOWER(CONCAT('%', :name, '%')))")
  Page<Doctor> findByStablishmentIdAndFilters(@Param("stablishmentId") Long stablishmentId, @Param("name") String name, @Param("ci") String ci, Pageable pageable);

  @Query("SELECT d FROM Doctor d JOIN d.services s WHERE s.id = :serviceId AND " +
         "(:name IS NULL OR :name = '' OR LOWER(CONCAT(d.firstName, ' ', d.lastName)) LIKE LOWER(CONCAT('%', :name, '%')) " +
         "OR LOWER(d.firstName) LIKE LOWER(CONCAT('%', :name, '%')) " +
         "OR LOWER(d.lastName) LIKE LOWER(CONCAT('%', :name, '%')))")
  Page<Doctor> findByServiceIdAndName(@Param("serviceId") Long serviceId, @Param("name") String name, Pageable pageable);
}
