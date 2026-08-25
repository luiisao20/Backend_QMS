package com.devluis.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devluis.dto.ScheduleTemplateDTO;
import com.devluis.dto.ServicioDTO;
import com.devluis.dto.StablishmentDTO;
import com.devluis.services.ScheduleTemplateService;

import tools.jackson.databind.ObjectMapper;

/**
 * Security filters are disabled: this slice only proves request/response
 * wiring to {@link ScheduleTemplateService}. The real
 * {@code SecurityFilterChain} lives in {@code GlobalConfig}, out of scope —
 * see its "administracion/horarios" comments for the public-GET rationale.
 */
@WebMvcTest(ScheduleTemplateController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScheduleTemplateControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ScheduleTemplateService scheduleTemplateService;

  @Autowired
  private ObjectMapper objectMapper;

  private ScheduleTemplateDTO validDto() {
    return ScheduleTemplateDTO.builder()
        .stablishment(StablishmentDTO.builder().id(2L).build())
        .servicio(ServicioDTO.builder().id(1L).build())
        .dayOfWeek(DayOfWeek.MONDAY)
        .startTime(LocalTime.of(8, 0))
        .endTime(LocalTime.of(12, 0))
        .slotIntervalMinutes(30)
        .validFrom(LocalDate.of(2026, 1, 1))
        .build();
  }

  @Test
  void create_delegatesToServiceAndReturns201() throws Exception {
    ScheduleTemplateDTO saved = validDto();
    saved.setId(10L);
    when(scheduleTemplateService.create(any(ScheduleTemplateDTO.class))).thenReturn(saved);

    mockMvc.perform(post("/api/schedule-templates/save")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validDto())))
        .andExpect(status().isCreated());

    verify(scheduleTemplateService).create(any(ScheduleTemplateDTO.class));
  }

  @Test
  void create_returns400_whenServicioIsMissing() throws Exception {
    ScheduleTemplateDTO invalid = validDto();
    invalid.setServicio(null);

    mockMvc.perform(post("/api/schedule-templates/save")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(invalid)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getAll_bindsStablishmentServiceAndDoctorFilters_andForwardsToService() throws Exception {
    UUID doctorUuid = UUID.randomUUID();
    when(scheduleTemplateService.getAll(eq(2L), eq(1L), eq(doctorUuid), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(validDto())));

    mockMvc.perform(get("/api/schedule-templates")
            .param("stablishmentId", "2")
            .param("serviceId", "1")
            .param("doctorId", doctorUuid.toString()))
        .andExpect(status().isOk());

    verify(scheduleTemplateService).getAll(eq(2L), eq(1L), eq(doctorUuid), any(Pageable.class));
  }

  @Test
  void getAll_withNoFilters_bindsAllAsNull() throws Exception {
    when(scheduleTemplateService.getAll(isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(Page.empty());

    mockMvc.perform(get("/api/schedule-templates"))
        .andExpect(status().isOk());

    verify(scheduleTemplateService).getAll(isNull(), isNull(), isNull(), any(Pageable.class));
  }

  @Test
  void getById_returns200WithBody() throws Exception {
    ScheduleTemplateDTO dto = validDto();
    dto.setId(7L);
    when(scheduleTemplateService.getById(7L)).thenReturn(dto);

    mockMvc.perform(get("/api/schedule-templates/7"))
        .andExpect(status().isOk());

    verify(scheduleTemplateService).getById(7L);
  }

  @Test
  void update_delegatesToServiceAndReturns200() throws Exception {
    ScheduleTemplateDTO updated = validDto();
    updated.setId(5L);
    when(scheduleTemplateService.update(eq(5L), any(ScheduleTemplateDTO.class))).thenReturn(updated);

    mockMvc.perform(put("/api/schedule-templates/5")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validDto())))
        .andExpect(status().isOk());

    verify(scheduleTemplateService).update(eq(5L), any(ScheduleTemplateDTO.class));
  }

  @Test
  void delete_delegatesToServiceAndReturns204() throws Exception {
    mockMvc.perform(delete("/api/schedule-templates/9"))
        .andExpect(status().isNoContent());

    verify(scheduleTemplateService).delete(9L);
  }
}
