package com.devluis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.BlockReason;
import com.devluis.types.BlockReasonKind;

public interface BlockReasonRepository extends JpaRepository<BlockReason, Long> {

  Optional<BlockReason> findByNameIgnoreCase(String name);

  List<BlockReason> findByActiveTrueOrderByNameAsc();

  List<BlockReason> findByKindAndActiveTrueOrderByNameAsc(BlockReasonKind kind);
}
