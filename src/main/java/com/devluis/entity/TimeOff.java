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

/**
 * Un doctor no atiende entre dos fechas.
 *
 * UNA TABLA PARA DOS PANTALLAS. Vacaciones y Permisos del panel administrativo
 * son la misma fila con distinto `kind`: el efecto sobre la agenda es idéntico y
 * las columnas serían las mismas. Dos tablas gemelas es una garantía de que una
 * de las dos se queda atrás cuando se agregue una columna.
 *
 * EL DUEÑO ES UN DOCTOR, no un empleado cualquiera, y eso es una decisión.
 * Este módulo se llama "Bloqueo de citas" y una cita se bloquea porque el
 * profesional que la iba a atender no está — la ausencia de un operador es un
 * problema de dotación, no de agenda. Si más adelante hace falta registrar
 * ausencias de operadores, esto necesita una columna de tipo de sujeto o una
 * tabla aparte; no alcanza con hacer el FK nullable, porque una fila sin doctor
 * no bloquea nada y quedaría muda.
 *
 * `startDate` / `endDate` y no `from` / `to`: `from` es palabra reservada de SQL
 * y una columna con ese nombre obliga a citarla en cada consulta.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "time_off")
@Entity
public class TimeOff {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "doctor_id", nullable = false)
  private Doctor doctor;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private TimeOffKind kind = TimeOffKind.TIMEOFF_VACATION;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  /** Inclusivo: una ausencia de un solo día lleva la misma fecha en las dos. */
  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "block_reason_id")
  private BlockReason reason;

  @Column(length = 500)
  private String notes;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
