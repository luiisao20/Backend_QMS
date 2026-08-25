package com.devluis.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devluis.dto.PatientDTO;
import com.devluis.services.PatientService;

/**
 * Security filters are disabled ({@code addFilters = false}) because this
 * slice only targets {@link PatientController}'s own logic: the routing
 * (literal "/me" vs "/{id}") and the Authentication-to-UUID resolution. The
 * real {@code SecurityFilterChain} lives in {@code GlobalConfig}, which is
 * intentionally out of scope here.
 */
@WebMvcTest(PatientController.class)
@AutoConfigureMockMvc(addFilters = false)
class PatientControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private PatientService patientService;

  @Test
  void getPatient_returnsPatientDto_whenServiceFindsIt() throws Exception {
    UUID id = UUID.randomUUID();
    PatientDTO dto = PatientDTO.builder()
        .uuid(id)
        .email("paciente@example.com")
        .firstName("Ana")
        .lastName("Perez")
        .ci("1234567890")
        .birthday(LocalDate.of(1990, 1, 1))
        .build();
    when(patientService.getPatientById(id)).thenReturn(dto);

    mockMvc.perform(get("/api/patients/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uuid").value(id.toString()))
        .andExpect(jsonPath("$.email").value("paciente@example.com"));
  }

  @Test
  void getPatient_returns400WithSpanishMessage_whenServiceReportsNotFound() throws Exception {
    UUID id = UUID.randomUUID();
    when(patientService.getPatientById(id)).thenThrow(new RuntimeException("Paciente no encontrado"));

    mockMvc.perform(get("/api/patients/{id}", id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Paciente no encontrado"));
  }

  @Test
  void getMyProfile_resolvesPatientFromAuthenticatedPrincipalUuid_returnsOk() throws Exception {
    UUID id = UUID.randomUUID();
    PatientDTO dto = PatientDTO.builder()
        .uuid(id)
        .email("yo@example.com")
        .firstName("Yo")
        .lastName("Mismo")
        .ci("0987654321")
        .birthday(LocalDate.of(1985, 5, 5))
        .build();
    when(patientService.getPatientById(id)).thenReturn(dto);

    Authentication auth = new UsernamePasswordAuthenticationToken(
        id.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));

    // "/me" must resolve to the literal @GetMapping("/me") handler, never to
    // @GetMapping("/{id}") trying (and failing) to parse "me" as a UUID.
    mockMvc.perform(get("/api/patients/me").principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("yo@example.com"));

    verify(patientService).getPatientById(id);
  }

  @Test
  void getMyProfile_returns400_whenServiceReportsNotFound() throws Exception {
    UUID id = UUID.randomUUID();
    when(patientService.getPatientById(id)).thenThrow(new RuntimeException("Paciente no encontrado"));
    Authentication auth = new UsernamePasswordAuthenticationToken(id.toString(), null, List.of());

    mockMvc.perform(get("/api/patients/me").principal(auth))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Paciente no encontrado"));
  }
}
