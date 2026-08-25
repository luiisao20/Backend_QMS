package com.devluis.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.Insurer;

public interface InsurerRepository extends JpaRepository<Insurer, Long> {

  Page<Insurer> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
