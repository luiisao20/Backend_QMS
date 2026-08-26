package com.devluis.services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Limite de mensajes por IP para el chat publico.
 *
 * POR QUE EXISTE: {@code POST /api/ai/chat} es el unico endpoint sin
 * autenticacion de este sistema que termina gastando tokens de un proveedor
 * externo. Sin tope, un script cualquiera agota la cuota de Gemini en
 * minutos y el chat deja de funcionar para los pacientes reales. El limite no
 * protege datos — no hay datos privados detras — protege la disponibilidad.
 *
 * LIMITACIONES, dichas de frente:
 *
 * - Es EN MEMORIA. Se reinicia con el contenedor y no se comparte entre
 *   replicas. Para el despliegue actual (un unico contenedor de backend, ver
 *   clinicore-infra/docker-compose.yml) alcanza. El dia que haya dos
 *   replicas, esto pasa a ser un limite por replica y hay que moverlo a Redis
 *   o al reverse proxy.
 * - Es por IP. Una clinica entera detras de un NAT comparte cuota, y alguien
 *   con IPs rotativas lo evade. Es una barrera contra el abuso trivial, no
 *   contra un atacante decidido. Para eso el lugar correcto es nginx
 *   ({@code limit_req_zone}) delante, no aqui.
 * - La ventana es fija, no deslizante: en el peor caso se permiten hasta 2x
 *   el limite en el cruce de dos ventanas. Aceptable para lo que protege.
 */
@Component
public class ChatRateLimiter {

  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  private final int maxPerWindow;
  private final long windowMillis;
  private final int maxTrackedClients;

  public ChatRateLimiter(
      @Value("${ai.chat.rate-limit.max-per-window:10}") int maxPerWindow,
      @Value("${ai.chat.rate-limit.window-seconds:60}") long windowSeconds,
      @Value("${ai.chat.rate-limit.max-tracked-clients:10000}") int maxTrackedClients) {
    this.maxPerWindow = maxPerWindow;
    this.windowMillis = windowSeconds * 1000L;
    this.maxTrackedClients = maxTrackedClients;
  }

  /**
   * @return true si el cliente puede enviar; false si ya paso su cuota.
   */
  public boolean tryConsume(HttpServletRequest request) {
    String client = resolveClient(request);
    long now = System.currentTimeMillis();

    // Cota superior de memoria: sin esto, una inundacion con X-Forwarded-For
    // falsificados hace crecer el mapa sin limite, y el rate limiter se
    // convierte en el vector de DoS que venia a evitar. Vaciarlo entero es
    // burdo pero seguro: el peor caso es regalar una ventana de cuota.
    if (windows.size() > maxTrackedClients) {
      windows.clear();
    }

    Window window = windows.compute(client, (key, existing) -> {
      if (existing == null || now - existing.startedAt > windowMillis) {
        return new Window(now);
      }
      return existing;
    });

    return window.count.incrementAndGet() <= maxPerWindow;
  }

  /**
   * Detras de nginx, {@code getRemoteAddr()} devuelve la IP del proxy y todos
   * los visitantes comparten cuota. El primer valor de X-Forwarded-For es el
   * cliente original (nginx lo setea, ver clinicore-infra/nginx/default.conf).
   *
   * Ese header lo puede falsificar quien llame al backend directo, sin pasar
   * por nginx — motivo de mas para que el puerto 8080 no este expuesto al
   * mundo, que es como esta el compose hoy.
   */
  private String resolveClient(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      int comma = forwarded.indexOf(',');
      String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
      return first.trim();
    }
    String remote = request.getRemoteAddr();
    return remote == null ? "desconocido" : remote;
  }

  private static final class Window {
    private final long startedAt;
    private final AtomicInteger count = new AtomicInteger(0);

    private Window(long startedAt) {
      this.startedAt = startedAt;
    }
  }
}
