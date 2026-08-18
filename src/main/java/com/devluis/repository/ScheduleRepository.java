package com.devluis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.Schedule;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

}
