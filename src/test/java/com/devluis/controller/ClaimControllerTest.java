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

import com.devluis.dto.ClaimDTO;
import com.devluis.services.ClaimService;
import com.devluis.types.ClaimStatus;

@WebMvcTest(ClaimController.class)
@AutoConfigureMockMvc(addFilters = false)
class ClaimControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ClaimService claimService;

  private Authentication staffAuth() {
    return new UsernamePasswordAuthenticationToken(
        UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
  }

  @Test
  void create_returns201_withTheSubmittedClaim() throws Exception {
    ClaimDTO dto = ClaimDTO.builder().id(1L).invoiceId(5L)
        .amountClaimed(new BigDecimal("80.00")).status(ClaimStatus.SUBMITTED).build();
    when(claimService.create(5L)).thenReturn(dto);

    mockMvc.perform(post("/api/claims")
            .principal(staffAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"invoiceId\":5}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amountClaimed").value(80.00))
        .andExpect(jsonPath("$.status").value("SUBMITTED"));
  }

  @Test
  void create_returns400WithSpanishMessage_whenInvoiceHasNoCoveredAmount() throws Exception {
    when(claimService.create(5L))
        .thenThrow(new RuntimeException("La factura no tiene monto cubierto por un asegurador para reclamar"));

    mockMvc.perform(post("/api/claims")
            .principal(staffAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"invoiceId\":5}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("La factura no tiene monto cubierto por un asegurador para reclamar"));
  }

  @Test
  void search_returns200() throws Exception {
    ClaimDTO dto = ClaimDTO.builder().id(1L).status(ClaimStatus.SUBMITTED).amountClaimed(new BigDecimal("80.00")).build();
    when(claimService.search(eq(null), eq(ClaimStatus.SUBMITTED), any())).thenReturn(new PageImpl<>(List.of(dto)));

    mockMvc.perform(get("/api/claims").principal(staffAuth()).param("status", "SUBMITTED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].amountClaimed").value(80.00));
  }

  @Test
  void getById_returns200() throws Exception {
    ClaimDTO dto = ClaimDTO.builder().id(9L).status(ClaimStatus.ACCEPTED).amountClaimed(new BigDecimal("40.00")).build();
    when(claimService.getById(9L)).thenReturn(dto);

    mockMvc.perform(get("/api/claims/{id}", 9L).principal(staffAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"));
  }

  @Test
  void accept_returns200_withTheAcceptedClaim() throws Exception {
    ClaimDTO dto = ClaimDTO.builder().id(1L).status(ClaimStatus.ACCEPTED).build();
    when(claimService.accept(1L)).thenReturn(dto);

    mockMvc.perform(put("/api/claims/{id}/accept", 1L).principal(staffAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"));
  }

  @Test
  void reject_returns200_withTheRejectedClaim() throws Exception {
    ClaimDTO dto = ClaimDTO.builder().id(1L).status(ClaimStatus.REJECTED).rejectionReason("Sin cobertura vigente").build();
    when(claimService.reject(1L, "Sin cobertura vigente")).thenReturn(dto);

    mockMvc.perform(put("/api/claims/{id}/reject", 1L)
            .principal(staffAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"Sin cobertura vigente\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.rejectionReason").value("Sin cobertura vigente"));
  }

  @Test
  void reject_returns400_whenReasonIsMissing() throws Exception {
    mockMvc.perform(put("/api/claims/{id}/reject", 1L)
            .principal(staffAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void markAsPaid_returns200_withThePaidClaim() throws Exception {
    ClaimDTO dto = ClaimDTO.builder().id(1L).status(ClaimStatus.PAID).build();
    when(claimService.markAsPaid(eq(1L), any(UUID.class))).thenReturn(dto);

    mockMvc.perform(put("/api/claims/{id}/mark-paid", 1L).principal(staffAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"));
  }
}
