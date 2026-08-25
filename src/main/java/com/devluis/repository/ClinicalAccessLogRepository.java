package com.devluis.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.ClinicalAccessLog;

public interface ClinicalAccessLogRepository extends JpaRepository<ClinicalAccessLog, Long> {
  Page<ClinicalAccessLog> findByPatientUuid(UUID patientUuid, Pageable pageable);
}
