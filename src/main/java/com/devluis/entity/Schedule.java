package com.devluis.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import com.devluis.types.ScheduleStatus;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "schedules")
@Entity
public class Schedule {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private LocalDate date;

  @Column(nullable = false)
  private LocalTime hour;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private ScheduleStatus status = ScheduleStatus.STATUS_FREE;

  // Optimistic-locking guard against double booking: two concurrent bookings
  // that both read STATUS_FREE will both try to flip it to STATUS_OCCUPIED,
  // but only the first UPDATE ... WHERE id = ? AND version = ? commits. The
  // loser gets an ObjectOptimisticLockingFailureException, translated by
  // TurnService.occupySchedule into a clear Spanish message. See the apply
  // report for why this was chosen over pessimistic locking or a unique
  // constraint, and for the required one-off backfill of this column.
  @Version
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "doctor_id")
  private Doctor doctor;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "service_id")
  private Servicio service;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "stablishment_id")
  private Stablishment stablishment;

  /**
   * Consultorio del cupo, COPIADO de la plantilla al generarlo, no leido de
   * ella en vivo.
   *
   * La copia es el punto: una plantilla se edita y se borra. Si el cupo leyera
   * la plantilla al vuelo, cambiar el horario del lunes que viene reescribiria
   * en que consultorio se atendio a un paciente el mes pasado. Mismo criterio
   * que InvoiceLineItem.sourceId, que es un Long pelado justamente para que una
   * factura emitida no pueda alcanzar el catalogo vivo.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "consultorio_id")
  private Consultorio consultorio;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;

  // No cascade: a Turn is never disposable. Deleting a Schedule must NOT
  // silently destroy booked turns. ScheduleService.delete (and the cascading
  // deletes from Stablishment/Servicio/Doctor) verify there are no turns left
  // via TurnRepository.existsByScheduleId/... BEFORE removing the schedule.
  @OneToMany(mappedBy = "schedule")
  private List<Turn> turns;
}
