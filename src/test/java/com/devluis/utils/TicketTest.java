package com.devluis.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TicketTest {

  @Test
  void format_joinsPrefixAndThreeDigitOrder() {
    assertThat(Ticket.format("B", 42)).isEqualTo("B-042");
  }

  @Test
  void format_padsToThreeDigits() {
    assertThat(Ticket.format("A", 1)).isEqualTo("A-001");
  }

  /** Un servicio con mas de 999 turnos en un dia no se trunca: crece. */
  @Test
  void format_doesNotTruncateOrdersAboveThreeDigits() {
    assertThat(Ticket.format("C", 1042)).isEqualTo("C-1042");
  }

  /**
   * Los servicios que ya existen no tienen prefijo. Caen al numero pelado, que
   * es exactamente lo que la pantalla mostraba antes de que el prefijo existiera.
   */
  @Test
  void format_fallsBackToThePaddedNumber_whenThereIsNoPrefix() {
    assertThat(Ticket.format(null, 42)).isEqualTo("042");
    assertThat(Ticket.format("", 42)).isEqualTo("042");
    assertThat(Ticket.format("   ", 42)).isEqualTo("042");
  }

  /** Sin orden no hay ticket. Null, no la cadena "null" pintada en un televisor. */
  @Test
  void format_returnsNull_whenThereIsNoOrder() {
    assertThat(Ticket.format("B", null)).isNull();
  }

  @Test
  void format_trimsAndUppercasesThePrefix() {
    assertThat(Ticket.format(" b ", 7)).isEqualTo("B-007");
  }
}
