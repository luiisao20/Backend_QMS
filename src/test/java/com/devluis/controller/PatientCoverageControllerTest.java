package com.devluis.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

import com.devluis.dto.CoverageQuoteDTO;
import com.devluis.dto.PatientCoverageDTO;
import com.devluis.services.PatientCoverageService;

/**
 * Security filters disabled ({@code addFilters = false}), same accepted
 * limitation as every other {@code @WebMvcTest} slice in this codebase (see
 * EncounterControllerTest/PatientControllerTest docblocks): neither
 * {@code @PreAuthorize} nor GlobalConfig's URL matchers run here. The
 * per-record "is this MY OWN coverage" denial is proven at the service layer
 * (PatientCoverageServiceTest + PatientCoverageAccessGuardTest).
 */
@WebMvcTest(PatientCoverageController.class)
@AutoConfigureMockMvc(addFilters = false)
class PatientCoverageControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private PatientCoverageService patientCoverageService;

  private Authentication patientAuth(UUID patientUuid) {
    return new UsernamePasswordAuthenticationToken(
        patientUuid.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
  }

  private Authentication staffAuth() {
    return new UsernamePasswordAuthenticationToken(
        UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
  }

  @Test
  void create_returns201_withTheCreatedCoverage() throws Exception {
    PatientCoverageDTO dto = PatientCoverageDTO.builder().id(1L).policyNumber("POL-1").build();
    when(patientCoverageService.create(any(PatientCoverageDTO.class))).thenReturn(dto);

    mockMvc.perform(post("/api/patient-coverages")
            .principal(staffAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"patient\":{\"uuid\":\"" + UUID.randomUUID() + "\"},"
                + "\"plan\":{\"id\":1},\"policyNumber\":\"POL-1\","
                + "\"validFrom\":\"2026-01-01\",\"active\":true}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.policyNumber").value("POL-1"));
  }

  @Test
  void create_returns400WithSpanishMessage_whenServiceRejectsTheDateRange() throws Exception {
    when(patientCoverageService.create(any(PatientCoverageDTO.class)))
        .thenThrow(new RuntimeException("La fecha de fin de vigencia no puede ser anterior a la fecha de inicio"));

    mockMvc.perform(post("/api/patient-coverages")
            .principal(staffAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"patient\":{\"uuid\":\"" + UUID.randomUUID() + "\"},"
                + "\"plan\":{\"id\":1},\"policyNumber\":\"POL-1\","
                + "\"validFrom\":\"2026-01-01\",\"validUntil\":\"2025-01-01\",\"active\":true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("La fecha de fin de vigencia no puede ser anterior a la fecha de inicio"));
  }

  @Test
  void getMyCoverages_resolvesThePatientUuidFromTheAuthenticatedPrincipal() throws Exception {
    UUID patientUuid = UUID.randomUUID();
    PatientCoverageDTO dto = PatientCoverageDTO.builder().id(1L).policyNumber("POL-1").build();
    when(patientCoverageService.listForPatient(patientUuid)).thenReturn(List.of(dto));

    mockMvc.perform(get("/api/patient-coverages/me").principal(patientAuth(patientUuid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].policyNumber").value("POL-1"));
  }

  @Test
  void quoteForMe_resolvesPatientUuidAndServicioId_returnsTheQuote() throws Exception {
    UUID patientUuid = UUID.randomUUID();
    CoverageQuoteDTO quote = CoverageQuoteDTO.builder()
        .servicioId(7L).hasCoverage(true).patientPays(new BigDecimal("20.00")).build();
    when(patientCoverageService.quoteForPatient(patientUuid, 7L)).thenReturn(quote);

    mockMvc.perform(get("/api/patient-coverages/me/quote")
            .principal(patientAuth(patientUuid))
            .param("servicioId", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.patientPays").value(20.00))
        .andExpect(jsonPath("$.hasCoverage").value(true));
  }

  @Test
  void quoteForMe_returns400_whenServicioDoesNotExist() throws Exception {
    UUID patientUuid = UUID.randomUUID();
    when(patientCoverageService.quoteForPatient(eq(patientUuid), eq(999L)))
        .thenThrow(new RuntimeException("Servicio no encontrado"));

    mockMvc.perform(get("/api/patient-coverages/me/quote")
            .principal(patientAuth(patientUuid))
            .param("servicioId", "999"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Servicio no encontrado"));
  }

  @Test
  void getById_returns200_withTheCoverage() throws Exception {
    PatientCoverageDTO dto = PatientCoverageDTO.builder().id(5L).policyNumber("POL-5").build();
    when(patientCoverageService.getById(eq(5L), any(Authentication.class))).thenReturn(dto);

    mockMvc.perform(get("/api/patient-coverages/{id}", 5L).principal(staffAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.policyNumber").value("POL-5"));
  }

  @Test
  void getById_returns400WithSpanishMessage_whenServiceDeniesTheCaller() throws Exception {
    when(patientCoverageService.getById(eq(5L), any(Authentication.class)))
        .thenThrow(new RuntimeException("Error de permisos: no tienes acceso a esta cobertura"));

    mockMvc.perform(get("/api/patient-coverages/{id}", 5L).principal(patientAuth(UUID.randomUUID())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Error de permisos: no tienes acceso a esta cobertura"));
  }

  @Test
  void getForPatient_returns200_forTheStaffScreen() throws Exception {
    UUID patientId = UUID.randomUUID();
    PatientCoverageDTO dto = PatientCoverageDTO.builder().id(2L).policyNumber("POL-2").build();
    when(patientCoverageService.listForPatient(patientId)).thenReturn(List.of(dto));

    mockMvc.perform(get("/api/patients/{patientId}/coverages", patientId).principal(staffAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].policyNumber").value("POL-2"));
  }

  @Test
  void update_returns200_withTheUpdatedCoverage() throws Exception {
    PatientCoverageDTO dto = PatientCoverageDTO.builder().id(9L).policyNumber("POL-9-CORREGIDA").build();
    when(patientCoverageService.update(eq(9L), any(PatientCoverageDTO.class))).thenReturn(dto);

    mockMvc.perform(put("/api/patient-coverages/{id}", 9L)
            .principal(staffAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"patient\":{\"uuid\":\"" + UUID.randomUUID() + "\"},"
                + "\"plan\":{\"id\":1},\"policyNumber\":\"POL-9-CORREGIDA\","
                + "\"validFrom\":\"2026-01-01\",\"active\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.policyNumber").value("POL-9-CORREGIDA"));
  }

  @Test
  void delete_returns204() throws Exception {
    mockMvc.perform(delete("/api/patient-coverages/{id}", 1L).principal(staffAuth()))
        .andExpect(status().isNoContent());
  }
}
