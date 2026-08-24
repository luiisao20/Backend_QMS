package com.devluis.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Catálogo de especialidades médicas.
 *
 * Existe para arreglar un problema concreto: `Doctor.speciality` es un String
 * libre, así que para la base de datos «Cardiología», «cardiologia» y
 * «Cardiologia» son tres especialidades distintas. Eso rompe dos cosas a la vez
 * — agrupar doctores por especialidad da basura, y el panel administrativo no
 * tiene nada que administrar en su pantalla de Especialidades.
 *
 * `name` es único a nivel de base de datos, no solo validado en el servicio:
 * este catálogo existe justamente para que no haya dos filas que signifiquen lo
 * mismo, y esa garantía tiene que vivir en la tabla.
 *
 * MIGRACIÓN: `Doctor` sigue teniendo su String y además una FK opcional a esta
 * tabla. Ver el comentario en `Doctor.specialityRef` — no se puede borrar la
 * columna de texto hasta que las filas existentes estén normalizadas, y eso es
 * una tarea de datos, no de código.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "specialities")
@Entity
public class Speciality {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String name;

  private String description;

  /**
   * Borrado lógico. Una especialidad que ya no se ofrece no se puede eliminar
   * sin dejar huérfanos a los doctores que la tienen asignada, así que se
   * desactiva: deja de aparecer en los desplegables y las filas históricas
   * siguen resolviendo su nombre.
   */
  @Column(nullable = false)
  @Builder.Default
  private Boolean active = true;

  // NO hay coleccion inversa de doctores a proposito, aunque el resto de las
  // entidades del proyecto las declaren. Lo unico que este catalogo necesita
  // saber de los doctores es CUANTOS hay por especialidad, y resolverlo
  // traversando la coleccion carga todas las filas de doctores por cada
  // especialidad de cada pagina — un N+1 para mostrar un numero.
  // `SpecialityRepository.countDoctorsBySpeciality()` lo hace en una consulta.

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
