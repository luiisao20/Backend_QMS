package com.devluis.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.Patient;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, UUID> {
  Optional<Patient> findByEmail(String email);

  Optional<Patient> findByCi(String email);
}
