package com.devluis.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.Stablishment;

public interface StablishmentRepository extends JpaRepository<Stablishment, Long> {
  Page<Stablishment> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
