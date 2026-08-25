package com.devluis.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.devluis.dto.PaymentDTO;
import com.devluis.services.PaymentService;
import com.devluis.types.PaymentMethod;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private PaymentService paymentService;

  private Authentication staffAuth() {
    return new UsernamePasswordAuthenticationToken(
        UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
  }

  @Test
  void create_returns201_withTheRecordedPayment() throws Exception {
    PaymentDTO dto = PaymentDTO.builder().id(1L).amount(new BigDecimal("30.00")).method(PaymentMethod.CASH).build();
    when(paymentService.create(eq(5L), any(PaymentDTO.class), any(Authentication.class))).thenReturn(dto);

    mockMvc.perform(post("/api/invoices/{invoiceId}/payments", 5L)
            .principal(staffAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":30.00,\"method\":\"CASH\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amount").value(30.00))
        .andExpect(jsonPath("$.method").value("CASH"));
  }

  @Test
  void create_returns400WithSpanishMessage_whenPaymentExceedsTheBalance() throws Exception {
    when(paymentService.create(eq(5L), any(PaymentDTO.class), any(Authentication.class)))
        .thenThrow(new RuntimeException("El pago excede el saldo pendiente de la factura. Saldo actual: 10.00"));

    mockMvc.perform(post("/api/invoices/{invoiceId}/payments", 5L)
            .principal(staffAuth())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":999.00,\"method\":\"CASH\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("El pago excede el saldo pendiente de la factura. Saldo actual: 10.00"));
  }

  @Test
  void getForInvoice_returns200_withThePaymentHistory() throws Exception {
    PaymentDTO dto = PaymentDTO.builder().id(1L).amount(new BigDecimal("15.00")).method(PaymentMethod.CARD).build();
    when(paymentService.getForInvoice(5L)).thenReturn(List.of(dto));

    mockMvc.perform(get("/api/invoices/{invoiceId}/payments", 5L).principal(staffAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].amount").value(15.00));
  }
}
