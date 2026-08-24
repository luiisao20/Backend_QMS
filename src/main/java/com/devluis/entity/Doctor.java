package com.devluis.entity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.devluis.types.Gender;
import com.devluis.types.Role;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "doctors")
@Entity
public class Doctor {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID uuid;

  @Column(unique = true, nullable = false)
  private String email;

  @Column(nullable = false)
  private String password;

  @Column(nullable = false)
  private String firstName;

  @Column(nullable = false)
  private String lastName;

  @Column(nullable = false)
  private String speciality;

  /**
   * La especialidad como fila del catálogo `specialities`.
   *
   * CONVIVE A PROPÓSITO con el String de arriba, y las dos columnas no son dos
   * fuentes de verdad: `DoctorService` copia el nombre del catálogo al String
   * cada vez que llega un `specialityId`, así que toda escritura NUEVA queda
   * consistente y el texto pasa a ser un espejo, no un dato independiente.
   *
   * Es nullable porque las filas que ya existen tienen texto libre sin
   * equivalente en el catálogo («cardiologia», «Cardiología»), y normalizarlas
   * es una tarea de datos: hay que decidir a mano qué fila del catálogo le
   * corresponde a cada variante. Recién cuando no quede ningún doctor con
   * `specialityRef` en null se puede borrar la columna de texto — antes de eso,
   * agrupar por especialidad tiene que seguir leyendo el String.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "speciality_id")
  private Speciality specialityRef;

  @Enumerated(EnumType.STRING)
  private Gender gender;

  @Column(nullable = false, unique = true)
  private String ci;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private Role role = Role.ROLE_DOCTOR;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;

  @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Schedule> schedules;

  @ManyToMany
  @JoinTable(name = "stablishment_has_doctors", joinColumns = @JoinColumn(name = "doctor_id"), inverseJoinColumns = @JoinColumn(name = "stablishment_id"))
  private List<Stablishment> stablishments;

  @ManyToMany
  @JoinTable(name = "doctor_has_services", joinColumns = @JoinColumn(name = "doctor_id"), inverseJoinColumns = @JoinColumn(name = "service_id"))
  private List<Servicio> services;
}
