package com.devluis.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devluis.services.LandingService;
import org.springframework.http.MediaType;

import lombok.Data;

/**
 * Datos de la landing publica.
 *
 * UNA RUTA POR SECCION, no un unico endpoint que devuelva todo junto. El
 * cliente ya tiene limites de error por seccion: cada organismo falla y se
 * reintenta solo, asi que una consulta caida apaga una banda y no la portada
 * entera. Un endpoint agregado convertiria cada falla en un fallo total.
 *
 * Ademas conviven dos frescuras muy distintas: hero.availability cambia con
 * cada reserva, mientras que las preguntas frecuentes cambian una vez por
 * trimestre. Juntarlas obliga a la politica de cache mas estricta para todo.
 *
 * PUBLICO, con matcher explicito en GlobalConfig. Es la portada de una clinica:
 * el visitante no tiene sesion. Ninguna seccion expone dato de paciente.
 */
@RestController
@RequestMapping("/api/landing")
@Data
public class LandingController {

  private final LandingService landingService;

  /**
   * {@code seccion} se valida contra una lista blanca en el servicio. Sin eso,
   * el nombre entra directo a una lectura del classpath y cualquiera podria
   * pedir rutas que no son secciones de la landing.
   */
  @GetMapping("/{seccion}")
  public ResponseEntity<String> getSeccion(@PathVariable String seccion) {
    if (!landingService.existe(seccion)) {
      return ResponseEntity.notFound().build();
    }
    // Content-Type explicito: el cuerpo ya viene serializado como texto y sin
    // esto saldria como text/plain, que el cliente no parsea.
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(landingService.get(seccion));
  }
}
