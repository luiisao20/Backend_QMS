package com.devluis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Lo que el widget de chat recibe de vuelta. Solo texto: el bot informa, no actua. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatResponseDTO {
  private String respuesta;
}
