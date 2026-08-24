package com.devluis.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.entity.Stablishment;

public interface StablishmentRepository extends JpaRepository<Stablishment, Long> {
  Page<Stablishment> findByNameContainingIgnoreCase(String name, Pageable pageable);

  @Query("SELECT e FROM Stablishment e JOIN e.services s WHERE s.id = :serviceId AND " +
      "(:name IS NULL OR :name = '' OR LOWER(e.name) LIKE LOWER(CONCAT('%', :name, '%')))")
  Page<Stablishment> findByServiceIdAndName(@Param("serviceId") Long serviceId, @Param("name") String name,
      Pageable pageable);
}
