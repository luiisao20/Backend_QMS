package com.devluis.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.devluis.dto.AccountingSummaryDTO;
import com.devluis.dto.ClaimStatusSummaryRow;
import com.devluis.dto.ClaimsSummaryDTO;
import com.devluis.services.AccountingService;
import com.devluis.types.ClaimStatus;

@WebMvcTest(AccountingController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountingControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AccountingService accountingService;

  private Authentication staffAuth() {
    return new UsernamePasswordAuthenticationToken(
        UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
  }

  @Test
  void getSummary_returns200_withTheAggregatedFigures() throws Exception {
    AccountingSummaryDTO dto = AccountingSummaryDTO.builder()
        .invoicedByStatus(List.of()).collectedByMethod(List.of())
        .outstandingNow(new BigDecimal("350.00")).build();
    when(accountingService.getSummary(any(), any())).thenReturn(dto);

    mockMvc.perform(get("/api/accounting/summary").principal(staffAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outstandingNow").value(350.00));
  }

  @Test
  void getClaimsSummary_returns200_withClaimsGroupedByStatus() throws Exception {
    ClaimsSummaryDTO dto = ClaimsSummaryDTO.builder().claimsByStatus(List.of(
        ClaimStatusSummaryRow.builder().status(ClaimStatus.PAID).count(3L).totalAmount(new BigDecimal("240.00")).build()))
        .build();
    when(accountingService.getClaimsSummary(any(), any())).thenReturn(dto);

    mockMvc.perform(get("/api/accounting/claims-summary").principal(staffAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.claimsByStatus[0].totalAmount").value(240.00));
  }
}
