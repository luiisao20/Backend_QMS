package com.devluis.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devluis.dto.TurnDTO;
import com.devluis.services.TurnService;
import com.devluis.types.TurnStatus;

/**
 * Security filters are disabled ({@code addFilters = false}): this slice only
 * targets routing and response shaping for the two new state-transition
 * endpoints. Role enforcement comes from {@code @PreAuthorize}, which is
 * method-security AOP wired by {@code @EnableMethodSecurity} in
 * {@code GlobalConfig} — a plain {@code @WebMvcTest} slice does not load that
 * configuration, so it is intentionally NOT exercised here. This mirrors the
 * same accepted limitation already documented for the equivalent
 * {@code @PreAuthorize} on {@code PatientController#getPatient} in
 * {@link PatientControllerTest}.
 */
@WebMvcTest(TurnController.class)
@AutoConfigureMockMvc(addFilters = false)
class TurnControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private TurnService turnService;

  private Authentication staffAuth() {
    return new UsernamePasswordAuthenticationToken(
        UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
  }

  private Authentication patientAuth() {
    return new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), null, List.of());
  }

  // TASK 1: a losing concurrent booking must reach the client as a clean
  // Spanish 400, not a raw 500 — end to end through GlobalExceptionHandler,
  // which @WebMvcTest DOES load (it scans @RestControllerAdvice beans).
  @Test
  void create_returns400WithSpanishMessage_whenTheSlotWasJustTakenConcurrently() throws Exception {
    when(turnService.create(any(TurnDTO.class), anyString()))
        .thenThrow(new RuntimeException(
            "Ese horario acaba de ser reservado por otra persona. Por favor, selecciona otro horario disponible."));

    mockMvc.perform(post("/api/turns")
            .principal(patientAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(
            "Ese horario acaba de ser reservado por otra persona. Por favor, selecciona otro horario disponible."));
  }

  @Test
  void markAsWaiting_returnsOk_whenServiceAcceptsTheTransition() throws Exception {
    TurnDTO dto = TurnDTO.builder().id(1L).status(TurnStatus.TURN_WAITNG).build();
    when(turnService.markAsWaiting(eq(1L), anyString())).thenReturn(dto);

    mockMvc.perform(put("/api/turns/{id}/waiting", 1L).principal(staffAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("TURN_WAITNG"));
  }

  @Test
  void markAsWaiting_returns400WithSpanishError_whenTransitionIsIllegal() throws Exception {
    when(turnService.markAsWaiting(eq(1L), anyString()))
        .thenThrow(new RuntimeException("Solo se puede registrar el ingreso de un turno que está pendiente"));

    mockMvc.perform(put("/api/turns/{id}/waiting", 1L).principal(staffAuth()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Solo se puede registrar el ingreso de un turno que está pendiente"));
  }

  @Test
  void markAsInTreatment_returnsOk_whenServiceAcceptsTheTransition() throws Exception {
    TurnDTO dto = TurnDTO.builder().id(1L).status(TurnStatus.TURN_IN_TREATMENT).build();
    when(turnService.markAsInTreatment(eq(1L), anyString())).thenReturn(dto);

    mockMvc.perform(put("/api/turns/{id}/in-treatment", 1L).principal(staffAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("TURN_IN_TREATMENT"));
  }

  @Test
  void markAsInTreatment_returns400WithSpanishError_whenTransitionIsIllegal() throws Exception {
    when(turnService.markAsInTreatment(eq(1L), anyString()))
        .thenThrow(new RuntimeException("Solo se puede iniciar la atención de un turno que está en sala de espera"));

    mockMvc.perform(put("/api/turns/{id}/in-treatment", 1L).principal(staffAuth()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Solo se puede iniciar la atención de un turno que está en sala de espera"));
  }
}
