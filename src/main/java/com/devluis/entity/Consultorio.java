package com.devluis.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un consultorio físico dentro de una sede.
 *
 * Pertenece a UN establecimiento y no puede existir sin él. Esa es la razón de
 * que sea una entidad propia y no un campo en Doctor: `Doctor.stablishments` es
 * @ManyToMany — un médico atiende en varias sedes, y "Consultorio 3" solo
 * significa algo dentro de una de ellas.
 *
 * La unicidad es por (sede, código), declarada en el esquema y no repetida en
 * cada consulta: dos sedes pueden tener cada una su "Consultorio 3", una sede
 * no puede tener dos. Con la restricción en la tabla, ninguna ruta de escritura
 * futura puede saltearse la regla — ni siquiera un INSERT hecho a mano.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "consultorios", uniqueConstraints = {
    @UniqueConstraint(name = "uk_consultorio_sede_codigo", columnNames = { "stablishment_id", "code" })
})
@Entity
public class Consultorio {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Lo que se pinta en la pantalla de sala: "03". Corto a propósito. */
  @Column(nullable = false, length = 8)
  private String code;

  /** El nombre legible: "Consultorio 3". Ver ConsultorioDTO sobre por qué son dos campos. */
  @Column(nullable = false)
  private String label;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "stablishment_id", nullable = false)
  private Stablishment stablishment;

  /**
   * Un consultorio fuera de servicio se desactiva, no se borra: las plantillas
   * y los turnos ya llamados siguen apuntando a él y deben seguir leyéndose.
   */
  @Builder.Default
  @Column(nullable = false)
  private Boolean active = true;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
