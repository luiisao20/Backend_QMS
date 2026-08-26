package com.devluis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `code` y `label` no son redundantes, y conviene no "simplificarlos" a uno.
 *
 * La pantalla de sala pinta `code` en el número grande de la fila ("03", que
 * tiene que entrar en una columna angosta y leerse de lejos) y `label` en el
 * panel principal ("Consultorio 3", que es lo que el paciente lee para saber a
 * qué puerta caminar). Derivar uno del otro obliga a la pantalla a inventar
 * formato, que es exactamente lo que un televisor no debería estar haciendo.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConsultorioDTO {
  private Long id;

  @NotBlank(message = "El código del consultorio es requerido")
  @Size(max = 8, message = "El código del consultorio no puede superar los 8 caracteres")
  private String code;

  @NotBlank(message = "El nombre del consultorio es requerido")
  private String label;

  @NotNull(message = "El establecimiento es requerido")
  private StablishmentDTO stablishment;

  private Boolean active;
}
