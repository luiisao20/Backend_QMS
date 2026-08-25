package com.devluis.entity;

import java.time.OffsetDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Issued during an Encounter. MANY-TO-ONE (not one-to-one): unlike
// Turn<->Encounter, nothing here requires "at most one prescription per
// encounter" — a doctor may reasonably issue more than one during the same
// consultation, so the more flexible multiplicity was kept instead of
// inventing a restriction nobody asked for.
//
// DELIBERATELY has no update()/PUT in PrescriptionService and no delete:
// once issued, a prescription is treated as an immutable, append-only
// record — real-world dispensing/legal consequences (a pharmacy may already
// be filling it) make "correct it in place" the wrong workflow for a
// prescription specifically, unlike Encounter's clinical notes, which DO
// support a correction PUT. See apply report for the full contrast.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "prescriptions")
@Entity
public class Prescription {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "encounter_id", nullable = false)
  private Encounter encounter;

  @Column(columnDefinition = "text")
  private String notes;

  // cascade=ALL + orphanRemoval=true is safe HERE (unlike Schedule->Turn's
  // deliberate no-cascade rule): a PrescriptionItem has no independent
  // existence or lifecycle outside its parent Prescription — it is not an
  // independently-referenced booking record, it is compositional data.
  @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<PrescriptionItem> items = new java.util.ArrayList<>();

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
