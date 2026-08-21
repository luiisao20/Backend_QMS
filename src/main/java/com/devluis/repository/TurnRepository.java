package com.devluis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.Turn;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TurnRepository extends JpaRepository<Turn, Long> {

  @Query("SELECT COUNT(t) FROM Turn t WHERE t.schedule.service.id = :serviceId AND t.schedule.date = :date")
  Long countTurnsByServiceAndDate(@Param("serviceId") Long serviceId, @Param("date") LocalDate date);

}
