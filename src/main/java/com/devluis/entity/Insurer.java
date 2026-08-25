package com.devluis.entity;

import java.time.OffsetDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import com.devluis.types.InsurerType;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Small catalog of insurance companies a patient may hold coverage with.
// `type` keeps the distinction the landing page already draws between
// privately-held plans (jsons/landing/insurers.json) and Ecuador's public
// insurance schemes (jsons/landing/public-insurance.json: IESS/ISSFA/ISSPOL/
// MSP) — unified into ONE table (matching the single `insurers` table the
// Flutter side already names in PersonalInfoScreen's docblock) instead of two
// parallel catalogs, because both kinds need the exact same downstream shape:
// a CoveragePlan and a PatientCoverage policy number/validity window. The
// per-insurer "documents you need to bring" text from public-insurance.json
// is DELIBERATELY not modelled here — it is static informational copy for
// the landing page, unrelated to CRUD/pricing, and stays served from that
// JSON. See apply report.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "insurers")
@Entity
public class Insurer {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InsurerType type;

  @OneToMany(mappedBy = "insurer")
  private List<CoveragePlan> plans;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
