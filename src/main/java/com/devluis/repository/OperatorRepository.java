package com.devluis.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.entity.Operator;

public interface OperatorRepository extends JpaRepository<Operator, UUID> {
  Optional<Operator> findByEmail(String email);

  @Query("SELECT o FROM Operator o WHERE o.stablishment.id = :stablishmentId AND " +
         "(:name IS NULL OR :name = '' OR LOWER(CONCAT(o.firstName, ' ', o.lastName)) LIKE LOWER(CONCAT('%', :name, '%')) " +
         "OR LOWER(o.firstName) LIKE LOWER(CONCAT('%', :name, '%')) " +
         "OR LOWER(o.lastName) LIKE LOWER(CONCAT('%', :name, '%')))")
  Page<Operator> findByStablishmentIdAndName(@Param("stablishmentId") Long stablishmentId, @Param("name") String name, Pageable pageable);
}
