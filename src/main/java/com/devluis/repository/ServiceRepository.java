package com.devluis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.Service;

public interface ServiceRepository extends JpaRepository<Service, Long> {

}
