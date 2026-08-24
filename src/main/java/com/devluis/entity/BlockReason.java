package com.devluis.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.devluis.types.BlockReasonKind;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Catálogo puro: por qué se bloqueó un espacio de agenda.
 *
 * `kind` es lo que hace que las tres pantallas que bloquean agenda puedan
 * ofrecer solo los motivos que les corresponden en vez de la lista entera — un
 * feriado no es un motivo válido para un permiso personal.
 *
 * Se desactiva, no se borra, por la misma razón que `Speciality`: hay filas de
 * `time_off` apuntando acá y su motivo tiene que seguir siendo legible.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "block_reasons")
@Entity
public class BlockReason {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private BlockReasonKind kind = BlockReasonKind.REASON_OTHER;

  @Column(nullable = false)
  @Builder.Default
  private Boolean active = true;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
