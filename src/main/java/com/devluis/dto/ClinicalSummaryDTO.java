package com.devluis.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lo que devuelve POST /api/patients/{patientId}/clinical-summary.
 *
 * Lleva el resumen Y los encuentros de origen a proposito. Ese es el mecanismo
 * que cumple el requisito "que no invente nada": no se le pide al modelo que
 * no alucine y se confia, se le pone al medico los registros reales al lado
 * para que si el resumen dice algo que no esta en la ficha, se note en dos
 * segundos. El resumen es una ayuda de lectura rapida, no una fuente.
 *
 * {@code totalEncuentros} vs {@code encuentrosResumidos} existe para que la
 * pantalla pueda decir "resumen de los ultimos 20 de 34 encuentros" en vez de
 * mentir por omision. Un resumen truncado en silencio se lee como completo.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClinicalSummaryDTO {

  /** El texto generado. Nunca un diagnostico ni una recomendacion: ver el system prompt del workflow. */
  private String resumen;

  /** Los registros exactos con los que se genero, para que el medico verifique. */
  private List<EncounterDTO> encuentros;

  /** Recetas de esos encuentros, tambien para verificacion. */
  private List<PrescriptionDTO> recetas;

  /** Cuantos encuentros tiene el paciente en total (segun lo que este usuario puede ver). */
  private long totalEncuentros;

  /** Cuantos entraron efectivamente al resumen. Si es menor que el total, esta truncado. */
  private int encuentrosResumidos;
}
