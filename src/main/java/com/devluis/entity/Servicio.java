package com.devluis.entity;

import java.time.OffsetDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "services")
@Entity
public class Servicio {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  /**
   * Letra del ticket en la pantalla de sala: la "B" de "B-042".
   *
   * NO es decoracion. TurnService calcula el orden con
   * countTurnsByServiceAndDate, o sea POR SERVICIO Y POR FECHA: el numero
   * reinicia en 1 para cada servicio cada dia, asi que dos servicios tienen
   * un turno #42 el mismo dia. Sin la letra el tablero es ambiguo y dos
   * pacientes caminan al mismo llamado.
   *
   * NULLABLE: los servicios que ya existen no tienen prefijo y el ticket cae
   * al numero pelado, que es lo que se mostraba hasta ahora.
   */
  @Column(length = 4)
  private String prefix;

  @Column(nullable = false)
  private Float price;

  private Float discount;

  @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Schedule> schedules;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;

  @ManyToMany(mappedBy = "services")
  private List<Stablishment> stablishments;
}
