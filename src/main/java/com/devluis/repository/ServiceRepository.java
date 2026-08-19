package com.devluis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.Servicio;

public interface ServiceRepository extends JpaRepository<Servicio, Long> {

}
