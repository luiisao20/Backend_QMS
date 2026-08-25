package com.devluis.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// One medication line within a Prescription. A single free-text field is not
// a prescription — this is why each medication carries its OWN dosage,
// frequency and duration instead of one shared text blob on Prescription.
// No separate Medication catalog/master-data entity: "medication" stays
// free text, since building a drug catalog/inventory was never asked for and
// would be pure speculation.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "prescription_items")
@Entity
public class PrescriptionItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "prescription_id", nullable = false)
  @JsonIgnore
  private Prescription prescription;

  @Column(nullable = false)
  private String medication;

  @Column(nullable = false)
  private String dosage;

  @Column(nullable = false)
  private String frequency;

  @Column(nullable = false)
  private String duration;

  @Column(columnDefinition = "text")
  private String instructions;
}
