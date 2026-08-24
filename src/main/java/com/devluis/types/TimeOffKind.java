package com.devluis.types;

/**
 * Tipo de ausencia de un doctor.
 *
 * Vacaciones y Permisos son dos pantallas del panel administrativo pero una
 * sola tabla: el efecto sobre la agenda es idéntico — el doctor no atiende
 * entre dos fechas — y lo único que cambia es cómo se llama. Dos tablas con las
 * mismas columnas es una garantía de que una de las dos se va a quedar atrás.
 */
public enum TimeOffKind {
  TIMEOFF_VACATION,
  TIMEOFF_PERMISSION
}
