package com.devluis.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devluis.dto.AdminModuleDTO;
import com.devluis.services.AdminModuleService;

/**
 * Security filters are disabled ({@code addFilters = false}) — same accepted
 * limitation as {@link BrandingControllerTest}: both GET and PUT here are
 * actually declared {@code hasAuthority("ROLE_ADMIN")} in
 * {@code GlobalConfig}, which this slice does not load. See the apply
 * report's manual curl checklist for the authorization-tier verification
 * this slice cannot perform.
 */
@WebMvcTest(AdminModuleController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminModuleControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AdminModuleService adminModuleService;

  @Test
  void getAllModules_returnsTheListFromTheService() throws Exception {
    when(adminModuleService.getAll()).thenReturn(List.of(
        AdminModuleDTO.builder().id(1L).moduleKey("dashboard").label("Dashboard").enabled(true).build()));

    mockMvc.perform(get("/api/admin-modules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].moduleKey").value("dashboard"))
        .andExpect(jsonPath("$[0].enabled").value(true));
  }

  @Test
  void setEnabled_returnsUpdatedModule_whenValid() throws Exception {
    when(adminModuleService.setEnabled(eq("precios"), eq(false)))
        .thenReturn(AdminModuleDTO.builder().id(6L).moduleKey("precios").label("Precios").enabled(false).build());

    mockMvc.perform(put("/api/admin-modules/{moduleKey}", "precios")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.moduleKey").value("precios"))
        .andExpect(jsonPath("$.enabled").value(false));
  }

  @Test
  void setEnabled_returns400_whenEnabledFieldMissing() throws Exception {
    mockMvc.perform(put("/api/admin-modules/{moduleKey}", "precios")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.enabled").exists());
  }

  @Test
  void setEnabled_returns400WithSpanishMessage_whenServiceRejectsSelfDisable() throws Exception {
    when(adminModuleService.setEnabled("modulos", false))
        .thenThrow(new RuntimeException("El módulo de gestión de módulos no se puede deshabilitar"));

    mockMvc.perform(put("/api/admin-modules/{moduleKey}", "modulos")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("El módulo de gestión de módulos no se puede deshabilitar"));
  }
}
