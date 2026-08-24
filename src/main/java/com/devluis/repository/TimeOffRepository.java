package com.devluis.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.entity.TimeOff;
import com.devluis.types.TimeOffKind;

public interface TimeOffRepository extends JpaRepository<TimeOff, Long> {

  /**
   * Todo filtro es opcional. El `kind` es lo que hace que Vacaciones y Permisos
   * sean dos pantallas sobre una sola tabla.
   *
   * El rango se compara SOLAPADO, no contenido: una ausencia del 1 al 30 tiene
   * que aparecer cuando se pregunta por el 15, aunque ni su inicio ni su fin
   * caigan dentro de la ventana consultada. Comparar `startDate BETWEEN from AND
   * to` es el error clásico acá y deja huecos justo en las ausencias largas.
   */
  @Query("SELECT t FROM TimeOff t WHERE " +
      "(:doctorId IS NULL OR t.doctor.uuid = :doctorId) AND " +
      "(:kind IS NULL OR t.kind = :kind) AND " +
      "(:from IS NULL OR t.endDate >= :from) AND " +
      "(:to IS NULL OR t.startDate <= :to) " +
      "ORDER BY t.startDate DESC")
  Page<TimeOff> search(
      @Param("doctorId") UUID doctorId,
      @Param("kind") TimeOffKind kind,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      Pageable pageable);

  /** Ausencias del doctor que se pisan con el rango dado — para rechazar duplicados. */
  @Query("SELECT t FROM TimeOff t WHERE t.doctor.uuid = :doctorId " +
      "AND t.endDate >= :from AND t.startDate <= :to " +
      "AND (:excludeId IS NULL OR t.id <> :excludeId)")
  List<TimeOff> findOverlapping(
      @Param("doctorId") UUID doctorId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("excludeId") Long excludeId);
}
