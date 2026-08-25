package com.devluis.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.BlockReason;

public interface BlockReasonRepository extends JpaRepository<BlockReason, Long> {
  Page<BlockReason> findByDescriptionContainingIgnoreCase(String description, Pageable pageable);
}
