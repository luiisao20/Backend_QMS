package com.devluis.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devluis.dto.LongCountRow;
import com.devluis.entity.Stablishment;

public interface StablishmentRepository extends JpaRepository<Stablishment, Long> {
  Page<Stablishment> findByNameContainingIgnoreCase(String name, Pageable pageable);

  @Query("SELECT e FROM Stablishment e JOIN e.services s WHERE s.id = :serviceId AND " +
      "(:name IS NULL OR :name = '' OR LOWER(e.name) LIKE LOWER(CONCAT('%', :name, '%')))")
  Page<Stablishment> findByServiceIdAndName(@Param("serviceId") Long serviceId, @Param("name") String name,
      Pageable pageable);

  @Query("SELECT e FROM Stablishment e JOIN e.doctors d WHERE d.uuid = :doctorId")
  List<Stablishment> findStablishmentsByDoctorId(@Param("doctorId") java.util.UUID doctorId);

  // --- Metrics aggregates (MetricsController / MetricsService) -------------
  // COUNT(DISTINCT ...) over a LEFT JOIN instead of SIZE(st.doctors) /
  // SIZE(st.services): SIZE()'s exact numeric return type is less predictable
  // across Hibernate versions, while COUNT(...) is unambiguously a Long per
  // the JPQL spec — safer given none of this can be run against a real DB
  // here. UNVERIFIED AGAINST A REAL DATABASE — see the apply report.

  @Query("SELECT new com.devluis.dto.LongCountRow(st.id, COUNT(DISTINCT d.uuid)) FROM Stablishment st " +
      "LEFT JOIN st.doctors d GROUP BY st.id")
  List<LongCountRow> countDoctorsPerStablishment();

  @Query("SELECT new com.devluis.dto.LongCountRow(st.id, COUNT(DISTINCT sv.id)) FROM Stablishment st " +
      "LEFT JOIN st.services sv GROUP BY st.id")
  List<LongCountRow> countServicesPerStablishment();
}
