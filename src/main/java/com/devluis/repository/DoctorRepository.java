package com.devluis.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.Doctor;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {
  Optional<Doctor> findByEmail(String email);
  Optional<Doctor> findByCi(String ci);
}
