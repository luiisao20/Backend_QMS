package com.devluis.dto;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de GET /api/sala/{sedeId}/pantalla.
 *
 * La forma esta fijada por el contrato que ya consume la pantalla
 * (jsons/sala/pantalla.json + su README). No inventar campos: el cliente
 * Angular castea el cuerpo a su modelo sin validarlo, asi que un nombre
 * distinto no da error, simplemente llega undefined y la TV pinta un hueco.
 *
 * REGLA QUE EL BACKEND TIENE QUE RESPETAR: `history` NO incluye el llamado
 * actual. La pantalla compone la columna izquierda como [current, ...history]
 * y pinta la primera fila en oro porque es el mismo dato del panel grande. Si
 * el backend mandara `current` tambien dentro de `history`, el turno saldria
 * duplicado en las dos primeras filas.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WaitingRoomScreenDTO {

  private Site site;
  private CurrentCall current;
  private List<Call> history;
  private String ticker;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class Site {
    /**
     * Id numerico, devuelto a proposito. La ruta puede direccionar la sede por
     * nombre ("/sala/matriz"), pero el topic STOMP es
     * /topic/stablishment/{id}/{fecha} y la pantalla no puede armarlo desde un
     * nombre. Resolverlo una vez aca evita un segundo viaje.
     */
    private Long stablishmentId;

    private String brand;
    /** "Sede Matriz - Av. Amazonas 123". Stablishment no modela ciudad aparte. */
    private String location;
  }

  /** Una fila del historial: lo minimo que entra en la columna angosta. */
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class Call {
    private String ticket;
    private String room;
    private OffsetDateTime calledAt;
  }

  /** El panel grande: lo mismo que una fila, mas lo que solo se lee de cerca. */
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class CurrentCall {
    private String ticket;
    private String room;
    private String roomLabel;
    private String specialty;
    private OffsetDateTime calledAt;
  }
}
