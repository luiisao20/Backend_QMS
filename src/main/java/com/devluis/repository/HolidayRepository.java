package com.devluis.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.entity.Holiday;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

  /**
   * Los feriados de un rango, con el mismo criterio que usa la agenda: los
   * nacionales (`stablishment` en null) aplican a todas las sedes, así que
   * siempre entran; los de sede solo cuando se pregunta por esa sede o por
   * ninguna en particular.
   */
  @Query("SELECT h FROM Holiday h WHERE " +
      "(:from IS NULL OR h.date >= :from) AND " +
      "(:to IS NULL OR h.date <= :to) AND " +
      "(:stablishmentId IS NULL OR h.stablishment IS NULL OR h.stablishment.id = :stablishmentId) " +
      "ORDER BY h.date ASC")
  Page<Holiday> search(
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("stablishmentId") Long stablishmentId,
      Pageable pageable);

  /**
   * Duplicados. No lo cubre un índice único porque `stablishment_id` es
   * nullable y en Postgres dos NULL no colisionan — ver el doc de `Holiday`.
   */
  @Query("SELECT h FROM Holiday h WHERE h.date = :date AND " +
      "((:stablishmentId IS NULL AND h.stablishment IS NULL) OR h.stablishment.id = :stablishmentId)")
  List<Holiday> findSameDay(@Param("date") LocalDate date, @Param("stablishmentId") Long stablishmentId);
}
