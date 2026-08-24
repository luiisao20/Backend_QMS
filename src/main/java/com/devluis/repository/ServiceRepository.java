package com.devluis.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

import com.devluis.entity.Servicio;

public interface ServiceRepository extends JpaRepository<Servicio, Long> {

    @Query("SELECT DISTINCT s FROM Servicio s JOIN s.schedules sch WHERE sch.doctor.uuid = :doctorId")
    List<Servicio> findServicesByDoctorId(@Param("doctorId") UUID doctorId);

    Page<Servicio> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("SELECT s FROM Servicio s JOIN s.stablishments est WHERE est.id = :stablishmentId AND " +
           "(:name IS NULL OR :name = '' OR LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%')))")
    Page<Servicio> findByStablishmentIdAndName(@Param("stablishmentId") Long stablishmentId, @Param("name") String name, Pageable pageable);
}
