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

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devluis.dto.EncounterDTO;
import com.devluis.services.EncounterService;

/**
 * Security filters disabled ({@code addFilters = false}), same accepted
 * limitation as every other {@code @WebMvcTest} slice in this codebase (see
 * PatientControllerTest/TurnControllerTest docblocks): {@code @PreAuthorize}
 * and GlobalConfig's URL matchers are NOT exercised here. The actual
 * "wrong reader" denial is proven at the service layer
 * (EncounterServiceTest) via ClinicalAccessGuard, which does not depend on
 * Spring Security's method-security AOP at all.
 */
@WebMvcTest(EncounterController.class)
@AutoConfigureMockMvc(addFilters = false)
class EncounterControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private EncounterService encounterService;

  private Authentication doctorAuth() {
    return new UsernamePasswordAuthenticationToken(
        UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_DOCTOR")));
  }

  @Test
  void create_returns201_withTheCreatedEncounter() throws Exception {
    EncounterDTO dto = EncounterDTO.builder().id(1L).diagnosis("Gripe").build();
    when(encounterService.create(any(EncounterDTO.class), any(Authentication.class))).thenReturn(dto);

    mockMvc.perform(post("/api/encounters")
            .principal(doctorAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"turnId\":1,\"reasonForVisit\":\"Dolor\",\"diagnosis\":\"Gripe\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.diagnosis").value("Gripe"));
  }

  @Test
  void create_returns400WithSpanishMessage_whenServiceDeniesTheCaller() throws Exception {
    when(encounterService.create(any(EncounterDTO.class), any(Authentication.class)))
        .thenThrow(new RuntimeException("Error de permisos: no tienes acceso a esta historia clínica"));

    mockMvc.perform(post("/api/encounters")
            .principal(doctorAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"turnId\":1,\"reasonForVisit\":\"Dolor\",\"diagnosis\":\"Gripe\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Error de permisos: no tienes acceso a esta historia clínica"));
  }

  @Test
  void getById_returns200_withTheEncounter() throws Exception {
    EncounterDTO dto = EncounterDTO.builder().id(5L).diagnosis("Migraña").build();
    when(encounterService.getById(eq(5L), any(Authentication.class))).thenReturn(dto);

    mockMvc.perform(get("/api/encounters/{id}", 5L).principal(doctorAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(5))
        .andExpect(jsonPath("$.diagnosis").value("Migraña"));
  }

  @Test
  void getById_returns400_whenServiceThrows() throws Exception {
    when(encounterService.getById(eq(404L), any(Authentication.class)))
        .thenThrow(new RuntimeException("Historia clínica no encontrada"));

    mockMvc.perform(get("/api/encounters/{id}", 404L).principal(doctorAuth()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Historia clínica no encontrada"));
  }

  @Test
  void getMyHistory_returns200_resolvingThePatientUuidFromTheAuthenticatedPrincipal() throws Exception {
    UUID patientUuid = UUID.randomUUID();
    Authentication patientAuth = new UsernamePasswordAuthenticationToken(
        patientUuid.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
    Page<EncounterDTO> page = new PageImpl<>(List.of(EncounterDTO.builder().id(1L).build()));
    when(encounterService.getMyHistory(eq(patientUuid), any(Authentication.class), any())).thenReturn(page);

    mockMvc.perform(get("/api/encounters/me").principal(patientAuth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(1));
  }

  @Test
  void getHistoryForPatient_returns200_forTheStaffScreen() throws Exception {
    UUID patientUuid = UUID.randomUUID();
    Page<EncounterDTO> page = new PageImpl<>(List.of(EncounterDTO.builder().id(2L).build()));
    when(encounterService.getHistoryForPatient(eq(patientUuid), any(Authentication.class), any())).thenReturn(page);

    mockMvc.perform(get("/api/patients/{patientId}/encounters", patientUuid).principal(doctorAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(2));
  }

  @Test
  void update_returns200_withTheUpdatedEncounter() throws Exception {
    EncounterDTO dto = EncounterDTO.builder().id(9L).diagnosis("Corregido").build();
    when(encounterService.update(eq(9L), any(EncounterDTO.class), any(Authentication.class))).thenReturn(dto);

    // turnId is echoed back even though update() ignores it: EncounterDTO's
    // @NotNull on turnId is create-only in spirit (see EncounterDTO), but
    // this codebase has no bean-validation groups infrastructure anywhere,
    // so the same @Valid DTO validates identically on both verbs — a real
    // edit form would have this value already loaded from the GET response.
    mockMvc.perform(put("/api/encounters/{id}", 9L)
            .principal(doctorAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"turnId\":1,\"reasonForVisit\":\"Dolor\",\"diagnosis\":\"Corregido\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.diagnosis").value("Corregido"));
  }

  // Deletion decision: Encounter is a legal clinical record and is never
  // hard-deletable through this API — no @DeleteMapping exists on this
  // controller at all. DISCOVERY (pre-existing, not introduced by this
  // change, applies to every controller in this codebase): an unmapped verb
  // on a mapped path raises HttpRequestMethodNotSupportedException, but
  // GlobalExceptionHandler's own catch-all `@ExceptionHandler(Exception.class)`
  // intercepts it before Spring's default resolver can turn it into a clean
  // 405, so the caller actually observes 500, not 405. Documented here, not
  // silently "fixed" — narrowing that catch-all is a separate, wider-blast
  // -radius change outside this task. See apply report.
  @Test
  void delete_isNotMapped_fallsThroughToTheGenericErrorHandler_provingNoDeleteRouteExists() throws Exception {
    mockMvc.perform(delete("/api/encounters/{id}", 1L).principal(doctorAuth()))
        .andExpect(status().isInternalServerError());
  }
}
