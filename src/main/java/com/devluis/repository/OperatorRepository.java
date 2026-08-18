package com.devluis.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.Operator;
import java.util.Optional;

public interface OperatorRepository extends JpaRepository<Operator, UUID> {
  Optional<Operator> findByEmail(String email);
}
