package com.devluis.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.devluis.types.TimeOffKind;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// A doctor unavailable over a date range (inclusive on both ends). One
// entity backs BOTH "vacaciones" and "permisos" admin destinations — `kind`
// is the only thing that tells them apart, since both are structurally the
// same fact: this doctor cannot be scheduled between startDate and endDate.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "time_offs")
@Entity
public class TimeOff {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "doctor_id", nullable = false)
  private Doctor doctor;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TimeOffKind kind;

  @Column(nullable = false)
  private LocalDate startDate;

  @Column(nullable = false)
  private LocalDate endDate;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reason_id", nullable = false)
  private BlockReason reason;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
