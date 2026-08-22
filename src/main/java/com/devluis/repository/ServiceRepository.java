package com.devluis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

import com.devluis.entity.Servicio;

public interface ServiceRepository extends JpaRepository<Servicio, Long> {

    @Query("SELECT DISTINCT s FROM Servicio s JOIN s.schedules sch WHERE sch.doctor.uuid = :doctorId")
    List<Servicio> findServicesByDoctorId(@Param("doctorId") UUID doctorId);

}
