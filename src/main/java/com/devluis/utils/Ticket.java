package com.devluis.utils;

/**
 * Formato del numero de turno que se pinta en la pantalla de sala: "B-042".
 *
 * Vive aca y no en la pantalla porque el prefijo desambigua un dato del
 * dominio, no es un adorno visual. TurnService calcula el orden con
 * countTurnsByServiceAndDate, o sea POR SERVICIO Y POR FECHA: el numero
 * reinicia en 1 para cada servicio cada dia. Dos servicios tienen un turno #42
 * el mismo dia y sin la letra el tablero es ambiguo.
 *
 * Si un cliente derivara este formato por su cuenta, cada pantalla nueva podria
 * inventar el suyo y el mismo turno se veria distinto en dos lugares.
 */
public final class Ticket {

  private static final int MIN_DIGITS = 3;

  private Ticket() {
  }

  /**
   * @param prefix letra del servicio; null o en blanco cae al numero pelado
   * @param order  orden del turno dentro de su servicio y fecha
   * @return "B-042", o null si no hay orden
   */
  public static String format(String prefix, Integer order) {
    if (order == null) {
      return null;
    }

    // %03d rellena hasta 3 digitos pero NO trunca: un servicio con mas de 999
    // turnos en un dia sigue mostrando el numero completo.
    String number = String.format("%0" + MIN_DIGITS + "d", order);

    if (prefix == null || prefix.isBlank()) {
      return number;
    }

    return prefix.trim().toUpperCase() + "-" + number;
  }
}
