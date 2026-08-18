package com.devluis.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.devluis.types.TurnStatus;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "turns")
@Entity
public class Turn {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Integer order;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private TurnStatus status = TurnStatus.TURN_PENDING;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;

  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime finishedAt;

  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime cancelledAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "operator_id")
  private Operator operator;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "patient_id")
  private Patient patient;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "schedule_id")
  private Schedule schedule;
}
