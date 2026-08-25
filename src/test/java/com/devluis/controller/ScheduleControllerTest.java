package com.devluis.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.mockito.ArgumentCaptor;

import com.devluis.services.ScheduleService;
import com.devluis.types.ScheduleStatus;

/**
 * Security filters are disabled: this slice only proves query-parameter
 * binding and forwarding to {@link ScheduleService#getAll}. The real
 * {@code SecurityFilterChain} lives in {@code GlobalConfig}, out of scope.
 */
@WebMvcTest(ScheduleController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScheduleControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ScheduleService scheduleService;

  @SuppressWarnings("unchecked")
  private void stubEmptyPage() {
    when(scheduleService.getAll(any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
        .thenReturn(Page.empty());
  }

  @Test
  void getAll_bindsServiceIdFromToAndStatus_andForwardsThemToTheService() throws Exception {
    stubEmptyPage();

    mockMvc.perform(get("/api/schedules")
            .param("serviceId", "7")
            .param("from", "2026-01-01")
            .param("to", "2026-01-31")
            .param("status", "STATUS_FREE"))
        .andExpect(status().isOk());

    ArgumentCaptor<Long> serviceIdCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<ScheduleStatus> statusCaptor = ArgumentCaptor.forClass(ScheduleStatus.class);

    verify(scheduleService).getAll(
        isNull(), isNull(), isNull(), isNull(),
        serviceIdCaptor.capture(), fromCaptor.capture(), toCaptor.capture(), statusCaptor.capture(),
        any(Pageable.class));

    assertThat(serviceIdCaptor.getValue()).isEqualTo(7L);
    assertThat(fromCaptor.getValue()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(toCaptor.getValue()).isEqualTo(LocalDate.of(2026, 1, 31));
    assertThat(statusCaptor.getValue()).isEqualTo(ScheduleStatus.STATUS_FREE);
  }

  @Test
  void getAll_withOnlyDateParam_keepsTheNewFiltersNull_backwardCompatible() throws Exception {
    stubEmptyPage();

    mockMvc.perform(get("/api/schedules").param("date", "2026-01-15"))
        .andExpect(status().isOk());

    ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);

    verify(scheduleService).getAll(
        dateCaptor.capture(), isNull(), isNull(), isNull(),
        isNull(), isNull(), isNull(), isNull(), any(Pageable.class));

    assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.of(2026, 1, 15));
  }
}
