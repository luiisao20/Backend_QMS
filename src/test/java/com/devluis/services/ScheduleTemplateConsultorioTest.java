package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devluis.dto.ConsultorioDTO;
import com.devluis.dto.ScheduleTemplateDTO;
import com.devluis.dto.ServicioDTO;
import com.devluis.dto.StablishmentDTO;
import com.devluis.entity.Consultorio;
import com.devluis.entity.ScheduleTemplate;
import com.devluis.entity.Servicio;
import com.devluis.entity.Stablishment;
import com.devluis.repository.ConsultorioRepository;
import com.devluis.repository.DoctorRepository;
import com.devluis.repository.ScheduleRepository;
import com.devluis.repository.ScheduleTemplateRepository;
import com.devluis.repository.ServiceRepository;
import com.devluis.repository.StablishmentRepository;

/**
 * El consultorio por defecto de una jornada.
 *
 * Nota: ScheduleTemplateServiceTest (454 lineas) fue borrado en el commit
 * 7f69968 "Tests deleted". Este archivo NO lo reemplaza: cubre unicamente el
 * consultorio. Las garantias que cubria aquel siguen sin test.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleTemplateConsultorioTest {

  @Mock
  private ScheduleTemplateRepository scheduleTemplateRepository;
  @Mock
  private StablishmentRepository stablishmentRepository;
  @Mock
  private ServiceRepository serviceRepository;
  @Mock
  private DoctorRepository doctorRepository;
  @Mock
  private ScheduleRepository scheduleRepository;
  @Mock
  private ConsultorioRepository consultorioRepository;

  private ScheduleTemplateService service;

  private static final Long SEDE_MATRIZ = 1L;
  private static final Long SEDE_NORTE = 2L;

  @BeforeEach
  void setUp() {
    service = new ScheduleTemplateService(
        scheduleTemplateRepository, stablishmentRepository, serviceRepository,
        doctorRepository, scheduleRepository, consultorioRepository);
  }

  private Servicio servicio() {
    return Servicio.builder().id(10L).name("Cardiologia").price(30f).build();
  }

  private Stablishment sede(Long id) {
    return Stablishment.builder()
        .id(id).name("Sede " + id).address("Calle " + id)
        .services(List.of(servicio()))
        .build();
  }

  private Consultorio consultorioDe(Long sedeId) {
    return Consultorio.builder()
        .id(77L).code("03").label("Consultorio 3")
        .stablishment(sede(sedeId))
        .active(true)
        .build();
  }

  /** Plantilla de pool (sin doctor) para no arrastrar las validaciones del medico. */
  private ScheduleTemplateDTO dto(Long consultorioId) {
    return ScheduleTemplateDTO.builder()
        .stablishment(StablishmentDTO.builder().id(SEDE_MATRIZ).build())
        .servicio(ServicioDTO.builder().id(10L).build())
        .dayOfWeek(DayOfWeek.MONDAY)
        .startTime(LocalTime.of(8, 0))
        .endTime(LocalTime.of(12, 0))
        .slotIntervalMinutes(20)
        .validFrom(LocalDate.of(2026, 9, 1))
        .consultorio(consultorioId == null ? null : ConsultorioDTO.builder().id(consultorioId).build())
        .build();
  }

  private void stubHappyPath() {
    when(stablishmentRepository.findById(SEDE_MATRIZ)).thenReturn(Optional.of(sede(SEDE_MATRIZ)));
    when(serviceRepository.findById(10L)).thenReturn(Optional.of(servicio()));
    when(scheduleTemplateRepository.existsOverlappingForPool(
        anyLong(), anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(false);
  }

  @Test
  void create_persistsConsultorio_whenItBelongsToTheSameStablishment() {
    stubHappyPath();
    when(consultorioRepository.findById(77L)).thenReturn(Optional.of(consultorioDe(SEDE_MATRIZ)));
    when(scheduleTemplateRepository.save(any(ScheduleTemplate.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ScheduleTemplateDTO created = service.create(dto(77L));

    assertThat(created.getConsultorio()).isNotNull();
    assertThat(created.getConsultorio().getCode()).isEqualTo("03");
    assertThat(created.getConsultorio().getLabel()).isEqualTo("Consultorio 3");
  }

  /**
   * La trampa. Sin esta guarda un admin puede asignar el Consultorio 3 de la
   * Matriz a una jornada de la Norte, y la pantalla de la Norte mandaria al
   * paciente a una puerta que no existe en ese edificio.
   */
  @Test
  void create_rejectsConsultorio_belongingToADifferentStablishment() {
    stubHappyPath();
    when(consultorioRepository.findById(77L)).thenReturn(Optional.of(consultorioDe(SEDE_NORTE)));

    assertThatThrownBy(() -> service.create(dto(77L)))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("establecimiento");

    verify(scheduleTemplateRepository, never()).save(any());
  }

  @Test
  void create_throwsSpanishMessage_whenConsultorioDoesNotExist() {
    stubHappyPath();
    when(consultorioRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(dto(404L)))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Consultorio");

    verify(scheduleTemplateRepository, never()).save(any());
  }

  /** Nullable a proposito: las plantillas que ya existen no tienen consultorio. */
  @Test
  void create_allowsNullConsultorio_soExistingTemplatesStayValid() {
    stubHappyPath();
    when(scheduleTemplateRepository.save(any(ScheduleTemplate.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ScheduleTemplateDTO created = service.create(dto(null));

    assertThat(created.getConsultorio()).isNull();
    verify(consultorioRepository, never()).findById(any());
  }
}
