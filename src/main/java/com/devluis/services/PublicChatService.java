package com.devluis.services;

import org.springframework.stereotype.Service;

import com.devluis.dto.ChatRequestDTO;
import com.devluis.dto.ChatResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * El chat de atencion al cliente de la landing.
 *
 * Contraste deliberado con {@link ClinicalSummaryService}: ese pide
 * autorizacion, de-identifica y deja asiento de auditoria porque toca datos
 * clinicos. Este no hace nada de eso, y esta bien: el agente de n8n detras
 * solo consulta los endpoints que ya son {@code permitAll()} en
 * {@code GlobalConfig} (doctores, agenda, sedes, servicios) — lo mismo que
 * cualquiera ve en el sitio publico. No hay nada que auditar porque no hay
 * lectura privilegiada.
 *
 * El limite del bot es que es ANONIMO, y eso se hace cumplir en el system
 * prompt del workflow, no aqui: si alguien pregunta "cual es mi turno" o da
 * una cedula, el bot redirige al login. Este servicio no tiene forma de saber
 * quien pregunta, y no debe tenerla. Un asistente que SI pueda responder por
 * un paciente concreto es otro endpoint, autenticado, con el patron del
 * resumen clinico.
 */
@Service
@RequiredArgsConstructor
public class PublicChatService {

  private static final String WEBHOOK = "patient-chat";

  private final N8nClient n8nClient;

  public ChatResponseDTO chat(ChatRequestDTO request) {
    ChatPayload payload = new ChatPayload(request.getSessionId(), request.getMensaje());

    N8nChatResponse respuesta = n8nClient.post(WEBHOOK, payload, N8nChatResponse.class);

    boolean vacia = respuesta == null
        || respuesta.getRespuesta() == null
        || respuesta.getRespuesta().isBlank();

    return ChatResponseDTO.builder()
        .respuesta(vacia
            ? "No pude procesar la consulta en este momento. Intente de nuevo en unos segundos."
            : respuesta.getRespuesta())
        .build();
  }

  // --------------------------------------------------------------------------
  // Contrato con el workflow "patient-chat" de n8n. El nodo AI Agent lee
  // {{ $json.body.mensaje }} y el nodo de memoria {{ $json.body.sessionId }},
  // asi que estos dos nombres son parte del contrato: cambiarlos rompe el flujo.
  // --------------------------------------------------------------------------

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  static class ChatPayload {
    private String sessionId;
    private String mensaje;
  }

  @Data
  @NoArgsConstructor
  static class N8nChatResponse {
    private String respuesta;
  }
}
