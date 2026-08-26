package com.devluis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lo que manda el widget de chat de la landing.
 *
 * Este es el UNICO cuerpo que entra por un endpoint publico de este sistema
 * que termina gastando tokens de un proveedor externo, asi que se valida con
 * mas cuidado que un DTO interno.
 *
 * {@code sessionId} lo genera el navegador (un UUID por pestania) y es lo que
 * agrupa los mensajes de una misma conversacion en la memoria del agente en
 * n8n. NO es una credencial y no identifica a nadie: el chat es anonimo por
 * diseño y nunca responde datos de un paciente concreto. Se valida el largo
 * solo para que no sirva como vector de inyeccion contra el nodo de memoria.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatRequestDTO {

  @NotBlank(message = "Falta el identificador de conversación")
  @Size(max = 64, message = "Identificador de conversación inválido")
  private String sessionId;

  /**
   * 500 caracteres alcanzan de sobra para "¿hay turnos el jueves?" y cortan
   * de raiz el mensaje de 50 KB que existe solo para quemar contexto.
   */
  @NotBlank(message = "El mensaje no puede estar vacío")
  @Size(max = 500, message = "El mensaje es demasiado largo (máximo 500 caracteres)")
  private String mensaje;
}
