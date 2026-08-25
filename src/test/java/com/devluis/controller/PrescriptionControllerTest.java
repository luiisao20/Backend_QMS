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

import com.devluis.dto.PrescriptionDTO;
import com.devluis.dto.PrescriptionItemDTO;
import com.devluis.services.PrescriptionService;

/**
 * Security filters disabled, same accepted limitation documented on every
 * other {@code @WebMvcTest} slice in this codebase — see
 * EncounterControllerTest's docblock.
 */
@WebMvcTest(PrescriptionController.class)
@AutoConfigureMockMvc(addFilters = false)
class PrescriptionControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private PrescriptionService prescriptionService;

  private Authentication doctorAuth() {
    return new UsernamePasswordAuthenticationToken(
        UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_DOCTOR")));
  }

  @Test
  void create_returns201_withTheCreatedPrescriptionAndItsItems() throws Exception {
    PrescriptionItemDTO itemDto = PrescriptionItemDTO.builder()
        .medication("Amoxicilina").dosage("500mg").frequency("cada 8 horas").duration("por 7 días").build();
    PrescriptionDTO dto = PrescriptionDTO.builder().id(1L).items(List.of(itemDto)).build();
    when(prescriptionService.create(any(PrescriptionDTO.class), any(Authentication.class))).thenReturn(dto);

    mockMvc.perform(post("/api/prescriptions")
            .principal(doctorAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"encounterId\":1,\"items\":[{\"medication\":\"Amoxicilina\",\"dosage\":\"500mg\","
                + "\"frequency\":\"cada 8 horas\",\"duration\":\"por 7 días\"}]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.items[0].medication").value("Amoxicilina"));
  }

  @Test
  void create_returns400_whenServiceRejectsAnEmptyItemsList() throws Exception {
    when(prescriptionService.create(any(PrescriptionDTO.class), any(Authentication.class)))
        .thenThrow(new RuntimeException("La receta debe tener al menos un medicamento"));

    mockMvc.perform(post("/api/prescriptions")
            .principal(doctorAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"encounterId\":1,\"items\":[{\"medication\":\"X\",\"dosage\":\"1\","
                + "\"frequency\":\"1\",\"duration\":\"1\"}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("La receta debe tener al menos un medicamento"));
  }

  @Test
  void getById_returns200_withThePrescription() throws Exception {
    PrescriptionDTO dto = PrescriptionDTO.builder().id(9L).items(List.of()).build();
    when(prescriptionService.getById(eq(9L), any(Authentication.class))).thenReturn(dto);

    mockMvc.perform(get("/api/prescriptions/{id}", 9L).principal(doctorAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(9));
  }

  @Test
  void getById_returns400_whenServiceDeniesTheCaller() throws Exception {
    when(prescriptionService.getById(eq(9L), any(Authentication.class)))
        .thenThrow(new RuntimeException("Error de permisos: no tienes acceso a esta historia clínica"));

    mockMvc.perform(get("/api/prescriptions/{id}", 9L).principal(doctorAuth()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getMyPrescriptions_returns200_resolvingThePatientUuidFromTheAuthenticatedPrincipal() throws Exception {
    UUID patientUuid = UUID.randomUUID();
    Authentication patientAuth = new UsernamePasswordAuthenticationToken(
        patientUuid.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
    Page<PrescriptionDTO> page = new PageImpl<>(List.of(PrescriptionDTO.builder().id(1L).items(List.of()).build()));
    when(prescriptionService.getMyPrescriptions(eq(patientUuid), any(Authentication.class), any())).thenReturn(page);

    mockMvc.perform(get("/api/prescriptions/me").principal(patientAuth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(1));
  }

  @Test
  void getHistoryForPatient_returns200_forTheStaffScreen() throws Exception {
    UUID patientUuid = UUID.randomUUID();
    Page<PrescriptionDTO> page = new PageImpl<>(List.of(PrescriptionDTO.builder().id(2L).items(List.of()).build()));
    when(prescriptionService.getHistoryForPatient(eq(patientUuid), any(Authentication.class), any())).thenReturn(page);

    mockMvc.perform(get("/api/patients/{patientId}/prescriptions", patientUuid).principal(doctorAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(2));
  }

  // Immutability decision: a Prescription cannot be edited or deleted once
  // issued — no @PutMapping/@DeleteMapping exist on this controller. Same
  // discovered GlobalExceptionHandler quirk as EncounterControllerTest:
  // an unmapped verb surfaces as 500 (its catch-all Exception handler runs
  // before Spring's default 405 resolution), not a clean 405 — documented,
  // not fixed here (see apply report).
  @Test
  void update_isNotMapped_fallsThroughToTheGenericErrorHandler_provingNoEditRouteExists() throws Exception {
    mockMvc.perform(put("/api/prescriptions/{id}", 1L)
            .principal(doctorAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isInternalServerError());
  }

  @Test
  void delete_isNotMapped_fallsThroughToTheGenericErrorHandler_provingNoDeleteRouteExists() throws Exception {
    mockMvc.perform(delete("/api/prescriptions/{id}", 1L).principal(doctorAuth()))
        .andExpect(status().isInternalServerError());
  }
}
