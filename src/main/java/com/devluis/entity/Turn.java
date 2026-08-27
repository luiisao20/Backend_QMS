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

  @Column(name = "turn_order", nullable = false)
  private Integer order;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private TurnStatus status = TurnStatus.TURN_PENDING;

  @Column(name = "reminder_sent")
  @Builder.Default
  private Boolean reminderSent = false;

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

  /**
   * El consultorio por el que REALMENTE salio este turno. Arranca con el del
   * cupo y el operador puede cambiarlo al llamar, porque un medico se muda de
   * consultorio y el paciente tiene que caminar a la puerta correcta hoy.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "consultorio_id")
  private Consultorio consultorio;

  /**
   * Momento del llamado (WAITNG -> IN_TREATMENT). La pantalla de sala ordena
   * el historial por este campo: createdAt es cuando se reservo el turno, que
   * puede ser de hace una semana y no dice nada sobre el orden de llamado.
   */
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime calledAt;
}
