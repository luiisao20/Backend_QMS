package com.devluis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.devluis.entity.Speciality;

public interface SpecialityRepository extends JpaRepository<Speciality, Long> {

  /** `IgnoreCase` a propósito: el catálogo existe para que «Cardiología» y
   *  «cardiologia» no puedan ser dos filas distintas. */
  Optional<Speciality> findByNameIgnoreCase(String name);

  List<Speciality> findByActiveTrueOrderByNameAsc();

  /**
   * Cuántos doctores tiene cada especialidad, en UNA consulta.
   *
   * La alternativa obvia — una colección inversa en `Speciality` y un `.size()`
   * en el mapeo — carga todas las filas de doctores de cada especialidad de cada
   * página para mostrar un número. Ver el comentario en la entidad.
   */
  @Query("SELECT d.specialityRef.id AS specialityId, COUNT(d) AS total FROM Doctor d " +
      "WHERE d.specialityRef IS NOT NULL GROUP BY d.specialityRef.id")
  List<DoctorCount> countDoctorsBySpeciality();

  /** Proyección de la consulta de arriba. */
  interface DoctorCount {
    Long getSpecialityId();

    Long getTotal();
  }
}
