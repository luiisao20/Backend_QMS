package com.devluis.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devluis.dto.BrandingDTO;
import com.devluis.services.BrandingService;

/**
 * Security filters are disabled ({@code addFilters = false}) — same accepted
 * limitation already documented in {@link PatientControllerTest} and
 * {@link TurnControllerTest}: the real {@code SecurityFilterChain} lives in
 * {@code GlobalConfig} (a plain {@code @Configuration} bean, not one of the
 * bean types a {@code @WebMvcTest} slice loads), where GET /api/branding is
 * declared {@code permitAll()} and PUT /api/branding is declared
 * {@code hasAuthority("ROLE_ADMIN")}. This slice proves routing, request
 * validation and response shaping only — it does NOT prove that
 * authorization tier. See the apply report for the manual curl checklist
 * that covers that gap.
 */
@WebMvcTest(BrandingController.class)
@AutoConfigureMockMvc(addFilters = false)
class BrandingControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private BrandingService brandingService;

  @Test
  void getBranding_returnsOkWithServiceBody() throws Exception {
    BrandingDTO dto = BrandingDTO.builder()
        .name("Clínica San Rafael")
        .primaryColor("#1A2B3C")
        .email("contacto@sanrafael.ec")
        .build();
    when(brandingService.get()).thenReturn(dto);

    mockMvc.perform(get("/api/branding"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Clínica San Rafael"))
        .andExpect(jsonPath("$.primaryColor").value("#1A2B3C"));
  }

  @Test
  void saveBranding_returnsUpdatedBody_whenValid() throws Exception {
    BrandingDTO saved = BrandingDTO.builder().id(1L).name("Clínica San Rafael").build();
    when(brandingService.save(any(BrandingDTO.class))).thenReturn(saved);

    mockMvc.perform(put("/api/branding")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Clínica San Rafael\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.name").value("Clínica San Rafael"));
  }

  @Test
  void saveBranding_returns400WithSpanishMessage_whenNameIsBlank() throws Exception {
    mockMvc.perform(put("/api/branding")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.name").value("El nombre de la clínica es requerido"));
  }

  @Test
  void saveBranding_returns400_whenPrimaryColorIsNotHex() throws Exception {
    mockMvc.perform(put("/api/branding")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Clínica San Rafael\",\"primaryColor\":\"blue\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.primaryColor").exists());
  }
}
