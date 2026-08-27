package com.devluis.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.devluis.dto.ChatRequestDTO;
import com.devluis.dto.ChatResponseDTO;
import com.devluis.dto.ClinicalSummaryDTO;
import com.devluis.services.ChatRateLimiter;
import com.devluis.services.ClinicalSummaryService;
import com.devluis.services.PublicChatService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Los endpoints que hablan con los asistentes de IA (n8n + Gemini).
 *
 * Sin {@code @RequestMapping} a nivel de clase, mismo criterio que
 * {@link EncounterController}: el resumen clinico es un sub-recurso del
 * paciente y vive junto a sus hermanos
 * ("/api/patients/{patientId}/encounters", ".../prescriptions"), porque es
 * exactamente lo que resume. Un futuro chat de pacientes NO es un
 * sub-recurso de nadie y va a colgar de "/api/ai/chat". Cada metodo lleva su
 * ruta completa.
 *
 * POST y no GET aunque no cree nada en la base: cada llamada gasta tokens de
 * un proveedor externo y deja un asiento en ClinicalAccessLog. No es
 * idempotente ni cacheable, y un GET invita a que el navegador o un proxy lo
 * repita solo.
 *
 * El gateo grueso de rol es {@code @PreAuthorize}, igual que en
 * {@link EncounterController}. La regla fina — "este doctor trato a ESTE
 * paciente" — vive en {@code ClinicalAccessGuard}, que llaman los servicios
 * de historial y recetas por debajo de
 * {@link ClinicalSummaryService#generate}. ROLE_EMPLOYEE queda afuera: es
 * personal de recepcion, no clinico.
 */
@RestController
@RequiredArgsConstructor
public class AiController {

  private final ClinicalSummaryService clinicalSummaryService;
  private final PublicChatService publicChatService;
  private final ChatRateLimiter chatRateLimiter;

  // El boton "Generar resumen" de la ficha del paciente en el panel
  // (/admin/pacientes/informacion/{id}), mientras el medico atiende.
  @PostMapping("/api/patients/{patientId}/clinical-summary")
  public ResponseEntity<ClinicalSummaryDTO> getClinicalSummary(
      @PathVariable UUID patientId, Authentication auth) {
    return ResponseEntity.ok(clinicalSummaryService.generate(patientId, auth));
  }

  /**
   * El widget de chat de la landing y de /agendar.
   *
   * PUBLICO A PROPOSITO: lo usa un visitante que todavia no se registro, y
   * declararlo {@code permitAll()} en GlobalConfig es obligatorio porque la
   * cadena termina en {@code anyRequest().authenticated()}. Sin esa regla, el
   * chat pediria login y no serviria para lo unico que existe.
   *
   * Sin {@code Authentication} en la firma, tambien a proposito: este endpoint
   * no debe poder saber quien pregunta ni siquiera cuando el visitante tiene
   * sesion. Si pudiera, la tentacion de responder "su turno es el jueves"
   * aparece sola, y el bot pasaria a exponer datos de paciente por un canal
   * pensado para informacion publica. El asistente autenticado, cuando exista,
   * va a ser OTRO endpoint.
   *
   * Devuelve 429 con la misma forma de cuerpo que una respuesta normal, para
   * que el widget lo muestre como un mensaje mas del bot en vez de tener que
   * manejar un formato de error aparte.
   */
  @PostMapping("/api/ai/chat")
  public ResponseEntity<ChatResponseDTO> chat(
      @Valid @RequestBody ChatRequestDTO request, HttpServletRequest httpRequest) {

    if (!chatRateLimiter.tryConsume(httpRequest)) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
          ChatResponseDTO.builder()
              .respuesta("Está enviando mensajes muy rápido. Espere unos segundos e intente de nuevo.")
              .build());
    }

    return ResponseEntity.ok(publicChatService.chat(request));
  }
}
