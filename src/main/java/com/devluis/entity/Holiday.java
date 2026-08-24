package com.devluis.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un día en el que no se agenda.
 *
 * `stablishment` es OPCIONAL y ahí está toda la utilidad de la tabla: en null
 * significa feriado nacional y aplica a todas las sedes; con valor significa que
 * esa sede sola no atiende — una fiesta cantonal, una jornada de mantenimiento.
 * Sin esa columna harían falta tantas filas como sedes por cada feriado
 * nacional, y agregar una sede obligaría a copiar el calendario entero.
 *
 * NO HAY UNIQUE COMPUESTO en (date, stablishment) aunque el duplicado no tenga
 * sentido: en Postgres dos NULL son distintos entre sí, así que un índice único
 * sobre una columna nullable no impide cargar el mismo feriado nacional dos
 * veces. La validación vive en `HolidayService`, donde sí se puede distinguir
 * "sin sede" de "otra sede".
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "holidays")
@Entity
public class Holiday {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private LocalDate date;

  @Column(nullable = false)
  private String name;

  /** En null aplica a todas las sedes. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "stablishment_id")
  private Stablishment stablishment;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
