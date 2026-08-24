package com.devluis.types;

/**
 * Para qué sirve un motivo de bloqueo.
 *
 * El catálogo es uno solo, pero cada pantalla que bloquea agenda quiere ofrecer
 * únicamente los motivos que le corresponden: un feriado no es un motivo válido
 * para un permiso personal. Sin esta columna el desplegable de las tres
 * pantallas muestra la lista entera.
 */
public enum BlockReasonKind {
  REASON_HOLIDAY,
  REASON_VACATION,
  REASON_PERMISSION,
  REASON_OTHER
}
