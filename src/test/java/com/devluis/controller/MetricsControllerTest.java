package com.devluis.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devluis.dto.DayTurnsDTO;
import com.devluis.dto.DoctorMetricsDTO;
import com.devluis.dto.EmployeesMetricsDTO;
import com.devluis.dto.EstablishmentMetricsDTO;
import com.devluis.dto.EstablishmentsMetricsDTO;
import com.devluis.dto.MetricsSummaryDTO;
import com.devluis.dto.OperatorMetricsDTO;
import com.devluis.dto.PatientsMetricsDTO;
import com.devluis.dto.TurnStatusBreakdownDTO;
import com.devluis.dto.TurnsSeriesDTO;
import com.devluis.services.MetricsService;
import com.devluis.types.TurnStatus;

/**
 * Security filters are disabled ({@code addFilters = false}): this slice only
 * targets routing, query-param binding and response shaping. Role
 * enforcement for "/api/metrics/**" lives in {@code GlobalConfig}
 * ({@code requestMatchers(HttpMethod.GET, ...).hasAnyAuthority(...)}), which
 * a plain {@code @WebMvcTest} slice does not load — same accepted limitation
 * already documented in {@link TurnControllerTest} and
 * {@link PatientControllerTest}.
 */
@WebMvcTest(MetricsController.class)
@AutoConfigureMockMvc(addFilters = false)
class MetricsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private MetricsService metricsService;

  private TurnStatusBreakdownDTO emptyBreakdown() {
    Map<TurnStatus, Long> byStatus = new java.util.EnumMap<>(TurnStatus.class);
    for (TurnStatus status : TurnStatus.values()) {
      byStatus.put(status, 0L);
    }
    return TurnStatusBreakdownDTO.builder().byStatus(byStatus).total(0L).build();
  }

  @Test
  void getSummary_returnsOkWithTheServicePayload() throws Exception {
    MetricsSummaryDTO dto = MetricsSummaryDTO.builder()
        .turnsToday(emptyBreakdown())
        .totalPatients(340)
        .totalDoctors(12)
        .totalOperators(8)
        .totalEstablishments(3)
        .totalServices(15)
        .build();
    when(metricsService.getSummary()).thenReturn(dto);

    mockMvc.perform(get("/api/metrics/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalPatients").value(340))
        .andExpect(jsonPath("$.totalDoctors").value(12))
        .andExpect(jsonPath("$.turnsToday.total").value(0))
        .andExpect(jsonPath("$.turnsToday.byStatus.TURN_PENDING").value(0));
  }

  @Test
  void getTurnsSeries_bindsFromToStablishmentAndService_andForwardsThemToTheService() throws Exception {
    TurnsSeriesDTO dto = TurnsSeriesDTO.builder()
        .from(LocalDate.of(2026, 8, 1))
        .to(LocalDate.of(2026, 8, 2))
        .stablishmentId(5L)
        .serviceId(10L)
        .days(List.of(
            DayTurnsDTO.builder().date(LocalDate.of(2026, 8, 1)).turns(emptyBreakdown()).build(),
            DayTurnsDTO.builder().date(LocalDate.of(2026, 8, 2)).turns(emptyBreakdown()).build()))
        .build();
    when(metricsService.getTurnsSeries(any(), any(), any(), any())).thenReturn(dto);

    mockMvc.perform(get("/api/metrics/turns")
            .param("from", "2026-08-01")
            .param("to", "2026-08-02")
            .param("stablishmentId", "5")
            .param("serviceId", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.days.length()").value(2))
        .andExpect(jsonPath("$.stablishmentId").value(5));

    ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
    verify(metricsService).getTurnsSeries(fromCaptor.capture(), toCaptor.capture(), org.mockito.ArgumentMatchers.eq(5L),
        org.mockito.ArgumentMatchers.eq(10L));
    assertThat(fromCaptor.getValue()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(toCaptor.getValue()).isEqualTo(LocalDate.of(2026, 8, 2));
  }

  @Test
  void getTurnsSeries_worksWithoutOptionalParams_forwardingNulls() throws Exception {
    when(metricsService.getTurnsSeries(any(), any(), any(), any())).thenReturn(
        TurnsSeriesDTO.builder().days(List.of()).build());

    mockMvc.perform(get("/api/metrics/turns")).andExpect(status().isOk());

    verify(metricsService).getTurnsSeries(isNull(), isNull(), isNull(), isNull());
  }

  @Test
  void getTurnsSeries_returns400WithSpanishMessage_whenServiceRejectsAnInvalidRange() throws Exception {
    when(metricsService.getTurnsSeries(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("La fecha 'desde' no puede ser posterior a la fecha 'hasta'"));

    mockMvc.perform(get("/api/metrics/turns").param("from", "2026-08-10").param("to", "2026-08-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("La fecha 'desde' no puede ser posterior a la fecha 'hasta'"));
  }

  @Test
  void getEstablishmentMetrics_returnsOkWithTheServicePayload() throws Exception {
    EstablishmentsMetricsDTO dto = EstablishmentsMetricsDTO.builder()
        .from(LocalDate.of(2026, 8, 1))
        .to(LocalDate.of(2026, 8, 24))
        .establishments(List.of(EstablishmentMetricsDTO.builder()
            .stablishmentId(1L)
            .name("Sede Norte")
            .doctorsCount(6)
            .servicesCount(4)
            .turns(emptyBreakdown())
            .totalSlots(10)
            .occupiedSlots(4)
            .occupancyRate(0.4)
            .build()))
        .build();
    when(metricsService.getEstablishmentMetrics(any(), any())).thenReturn(dto);

    mockMvc.perform(get("/api/metrics/establishments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.establishments[0].name").value("Sede Norte"))
        .andExpect(jsonPath("$.establishments[0].occupancyRate").value(0.4));
  }

  @Test
  void getEmployeesMetrics_returnsOkWithDoctorsAndOperators() throws Exception {
    UUID doctorId = UUID.randomUUID();
    UUID operatorId = UUID.randomUUID();
    EmployeesMetricsDTO dto = EmployeesMetricsDTO.builder()
        .from(LocalDate.of(2026, 8, 1))
        .to(LocalDate.of(2026, 8, 24))
        .doctors(List.of(DoctorMetricsDTO.builder()
            .doctorId(doctorId).firstName("Carla").lastName("Mendez").speciality("Cardiologia")
            .attended(20).cancelled(3).noShows(2).build()))
        .operators(List.of(OperatorMetricsDTO.builder()
            .operatorId(operatorId).firstName("Luis").lastName("Pena")
            .turnsHandled(11).cancelled(2).build()))
        .build();
    when(metricsService.getEmployeesMetrics(any(), any())).thenReturn(dto);

    mockMvc.perform(get("/api/metrics/employees"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.doctors[0].attended").value(20))
        .andExpect(jsonPath("$.doctors[0].noShows").value(2))
        .andExpect(jsonPath("$.operators[0].turnsHandled").value(11));
  }

  @Test
  void getPatientsMetrics_returnsOkWithTheServicePayload() throws Exception {
    PatientsMetricsDTO dto = PatientsMetricsDTO.builder()
        .from(LocalDate.of(2026, 8, 1))
        .to(LocalDate.of(2026, 8, 24))
        .newPatients(7)
        .turnsInPeriod(10)
        .cancelledInPeriod(2)
        .cancellationRate(0.2)
        .build();
    when(metricsService.getPatientsMetrics(any(), any())).thenReturn(dto);

    mockMvc.perform(get("/api/metrics/patients").param("from", "2026-08-01").param("to", "2026-08-24"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.newPatients").value(7))
        .andExpect(jsonPath("$.cancellationRate").value(0.2));
  }
}
