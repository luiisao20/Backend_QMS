package com.devluis.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devluis.dto.InvoiceDTO;
import com.devluis.services.InvoiceService;
import com.devluis.types.InvoiceStatus;

/**
 * Security filters disabled ({@code addFilters = false}), same accepted
 * limitation as every other {@code @WebMvcTest} slice in this codebase — the
 * per-record "is this MY OWN invoice" denial is proven at the service layer
 * (InvoiceServiceTest + InvoiceAccessGuardTest).
 */
@WebMvcTest(InvoiceController.class)
@AutoConfigureMockMvc(addFilters = false)
class InvoiceControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private InvoiceService invoiceService;

  private Authentication patientAuth(UUID patientUuid) {
    return new UsernamePasswordAuthenticationToken(
        patientUuid.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
  }

  private Authentication staffAuth() {
    return new UsernamePasswordAuthenticationToken(
        UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
  }

  private Authentication adminAuth() {
    return new UsernamePasswordAuthenticationToken(
        UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  @Test
  void create_returns201_withTheCreatedInvoice() throws Exception {
    InvoiceDTO dto = InvoiceDTO.builder().id(1L).total(new BigDecimal("100.00")).status(InvoiceStatus.ISSUED).build();
    when(invoiceService.create(any(InvoiceDTO.class))).thenReturn(dto);

    mockMvc.perform(post("/api/invoices")
            .principal(staffAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"patient\":{\"uuid\":\"" + UUID.randomUUID() + "\"},"
                + "\"items\":[{\"sourceType\":\"FREE_LINE\",\"description\":\"Ajuste\",\"amount\":100.00}]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.total").value(100.00))
        .andExpect(jsonPath("$.status").value("ISSUED"));
  }

  @Test
  void create_returns400WithSpanishMessage_whenServiceRejectsIt() throws Exception {
    when(invoiceService.create(any(InvoiceDTO.class)))
        .thenThrow(new RuntimeException("Solo se puede facturar un turno atendido"));

    mockMvc.perform(post("/api/invoices")
            .principal(staffAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"patient\":{\"uuid\":\"" + UUID.randomUUID() + "\"},"
                + "\"items\":[{\"sourceType\":\"TURN\",\"sourceId\":5}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Solo se puede facturar un turno atendido"));
  }

  @Test
  void getMyInvoices_resolvesThePatientUuidFromTheAuthenticatedPrincipal() throws Exception {
    UUID patientUuid = UUID.randomUUID();
    InvoiceDTO dto = InvoiceDTO.builder().id(1L).total(new BigDecimal("50.00")).status(InvoiceStatus.PAID).build();
    when(invoiceService.getForPatient(eq(patientUuid), any())).thenReturn(new PageImpl<>(List.of(dto)));

    mockMvc.perform(get("/api/invoices/me").principal(patientAuth(patientUuid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].total").value(50.00));
  }

  @Test
  void getById_returns200_withTheInvoice() throws Exception {
    InvoiceDTO dto = InvoiceDTO.builder().id(5L).total(new BigDecimal("75.00")).status(InvoiceStatus.ISSUED).build();
    when(invoiceService.getById(eq(5L), any(Authentication.class))).thenReturn(dto);

    mockMvc.perform(get("/api/invoices/{id}", 5L).principal(staffAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(75.00));
  }

  @Test
  void getById_returns400WithSpanishMessage_whenServiceDeniesTheCaller() throws Exception {
    when(invoiceService.getById(eq(5L), any(Authentication.class)))
        .thenThrow(new RuntimeException("Error de permisos: no tienes acceso a esta factura"));

    mockMvc.perform(get("/api/invoices/{id}", 5L).principal(patientAuth(UUID.randomUUID())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Error de permisos: no tienes acceso a esta factura"));
  }

  @Test
  void search_returns200_forTheStaffBrowseScreen() throws Exception {
    InvoiceDTO dto = InvoiceDTO.builder().id(2L).total(new BigDecimal("20.00")).status(InvoiceStatus.ISSUED).build();
    when(invoiceService.search(eq(null), eq(InvoiceStatus.ISSUED), any())).thenReturn(new PageImpl<>(List.of(dto)));

    mockMvc.perform(get("/api/invoices").principal(staffAuth()).param("status", "ISSUED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].total").value(20.00));
  }

  @Test
  void getForPatient_returns200_forTheNestedStaffScreen() throws Exception {
    UUID patientId = UUID.randomUUID();
    InvoiceDTO dto = InvoiceDTO.builder().id(3L).total(new BigDecimal("30.00")).status(InvoiceStatus.ISSUED).build();
    when(invoiceService.getForPatient(eq(patientId), any())).thenReturn(new PageImpl<>(List.of(dto)));

    mockMvc.perform(get("/api/patients/{patientId}/invoices", patientId).principal(staffAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].total").value(30.00));
  }

  @Test
  void voidInvoice_returns200_withTheVoidedInvoice() throws Exception {
    InvoiceDTO dto = InvoiceDTO.builder().id(1L).status(InvoiceStatus.VOID).voidReason("Error de digitación").build();
    when(invoiceService.voidInvoice(eq(1L), eq("Error de digitación"), any(UUID.class))).thenReturn(dto);

    mockMvc.perform(put("/api/invoices/{id}/void", 1L)
            .principal(adminAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"Error de digitación\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VOID"));
  }

  @Test
  void voidInvoice_returns400WithSpanishMessage_whenAlreadyFullyPaid() throws Exception {
    when(invoiceService.voidInvoice(eq(1L), any(), any(UUID.class)))
        .thenThrow(new RuntimeException("No se puede anular una factura completamente pagada"));

    mockMvc.perform(put("/api/invoices/{id}/void", 1L)
            .principal(adminAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"motivo\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("No se puede anular una factura completamente pagada"));
  }
}
