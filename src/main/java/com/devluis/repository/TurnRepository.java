package com.devluis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.Turn;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TurnRepository extends JpaRepository<Turn, Long> {

  @Query("SELECT COUNT(t) FROM Turn t WHERE t.schedule.service.id = :serviceId AND t.schedule.date = :date")
  Long countTurnsByServiceAndDate(@Param("serviceId") Long serviceId, @Param("date") LocalDate date);

  @Query("SELECT t FROM Turn t WHERE t.patient.uuid = :patientUuid " +
         "AND (:status IS NULL OR t.status = :status) " +
         "AND (:fromDate IS NULL OR t.schedule.date >= :fromDate) " +
         "AND (:toDate IS NULL OR t.schedule.date <= :toDate) " +
         "ORDER BY t.schedule.date DESC, t.order ASC")
  org.springframework.data.domain.Page<Turn> findTurnsForPatient(
      @Param("patientUuid") java.util.UUID patientUuid, 
      @Param("status") com.devluis.types.TurnStatus status, 
      @Param("fromDate") LocalDate fromDate, 
      @Param("toDate") LocalDate toDate, 
      org.springframework.data.domain.Pageable pageable);

}
