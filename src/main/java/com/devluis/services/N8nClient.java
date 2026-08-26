package com.devluis.services;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * El unico punto por el que este backend habla con n8n.
 *
 * Existe como componente propio, y no como un {@code RestClient} inyectado
 * suelto en cada servicio, por dos razones:
 *
 * 1. El secreto compartido ({@code n8n.shared-secret}) se agrega SIEMPRE, en
 *    un solo lugar. n8n valida ese header en el nodo Webhook ("Header Auth"),
 *    y es lo que impide que alguien que descubra la URL del webhook dispare
 *    los flujos de IA sin pasar por el backend. Si el header se agregara a
 *    mano en cada llamada, alcanzaria con olvidarlo una vez.
 *
 * 2. Los timeouts. n8n llama a Gemini, que puede tardar segundos y con
 *    reintentos activados bastante mas. Sin un read timeout explicito, un n8n
 *    colgado deja al medico esperando indefinidamente con el paciente
 *    enfrente. Mejor un error claro a los {@code n8n.timeout-seconds} que un
 *    spinner eterno.
 *
 * Nota de arquitectura: el navegador NUNCA llama a n8n. Llama a este backend,
 * que valida el JWT del usuario y recien entonces reenvia por la red interna.
 * n8n no publica puerto al mundo (ver clinicore-infra/docker-compose.yml).
 */
@Component
public class N8nClient {

  private static final String SECRET_HEADER = "X-Internal-Secret";

  private final RestClient restClient;
  private final String sharedSecret;

  public N8nClient(
      @Value("${n8n.webhook-base}") String webhookBase,
      @Value("${n8n.shared-secret}") String sharedSecret,
      @Value("${n8n.connect-timeout-seconds:10}") long connectTimeoutSeconds,
      @Value("${n8n.timeout-seconds:60}") long timeoutSeconds) {

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
    factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

    this.restClient = RestClient.builder()
        .baseUrl(webhookBase)
        .requestFactory(factory)
        .build();
    this.sharedSecret = sharedSecret;
  }

  /**
   * POST a un webhook de n8n. {@code path} es el nombre del webhook, sin
   * barra inicial: "clinical-summary", "patient-chat".
   *
   * Traduce cualquier fallo de transporte o de n8n a un {@link RuntimeException}
   * con mensaje legible, porque el que lo va a leer es un medico en consulta,
   * no un desarrollador mirando un stack trace.
   */
  public <T> T post(String path, Object body, Class<T> responseType) {
    try {
      return restClient.post()
          .uri("/{path}", path)
          .header(SECRET_HEADER, sharedSecret)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(responseType);
    } catch (RestClientException e) {
      throw new RuntimeException(
          "El asistente de IA no esta disponible en este momento. Intente de nuevo en unos segundos.", e);
    }
  }
}
