package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devluis.dto.ConsultorioDTO;
import com.devluis.dto.StablishmentDTO;
import com.devluis.entity.Consultorio;
import com.devluis.entity.Stablishment;
import com.devluis.repository.ConsultorioRepository;
import com.devluis.repository.ScheduleTemplateRepository;
import com.devluis.repository.StablishmentRepository;

/**
 * Un consultorio pertenece a UNA sede. Ese detalle es la razón de ser de esta
 * entidad: `Doctor.stablishments` es @ManyToMany, así que "Consultorio 3" solo
 * significa algo dentro de una sede, y el mismo código tiene que poder repetirse
 * entre sedes distintas.
 */
@ExtendWith(MockitoExtension.class)
class ConsultorioServiceTest {

  @Mock
  private ConsultorioRepository consultorioRepository;
  @Mock
  private StablishmentRepository stablishmentRepository;
  @Mock
  private ScheduleTemplateRepository scheduleTemplateRepository;

  private ConsultorioService consultorioService;

  @BeforeEach
  void setUp() {
    consultorioService = new ConsultorioService(
        consultorioRepository, stablishmentRepository, scheduleTemplateRepository);
  }

  private ConsultorioDTO dtoFor(Long stablishmentId, String code) {
    return ConsultorioDTO.builder()
        .code(code)
        .label("Consultorio " + code)
        .stablishment(StablishmentDTO.builder().id(stablishmentId).build())
        .build();
  }

  private Stablishment sede(Long id) {
    return Stablishment.builder().id(id).name("Sede " + id).build();
  }

  @Test
  void create_persistsConsultorio_whenStablishmentExistsAndCodeIsFree() {
    when(stablishmentRepository.findById(1L)).thenReturn(Optional.of(sede(1L)));
    when(consultorioRepository.existsByStablishmentIdAndCode(1L, "03")).thenReturn(false);
    when(consultorioRepository.save(any(Consultorio.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ConsultorioDTO created = consultorioService.create(dtoFor(1L, "03"));

    assertThat(created.getCode()).isEqualTo("03");
    assertThat(created.getStablishment().getId()).isEqualTo(1L);
    verify(consultorioRepository).save(any(Consultorio.class));
  }

  @Test
  void create_throwsSpanishMessage_whenStablishmentDoesNotExist() {
    when(stablishmentRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> consultorioService.create(dtoFor(99L, "03")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Establecimiento");

    verify(consultorioRepository, never()).save(any());
  }

  @Test
  void create_rejectsDuplicateCode_withinTheSameStablishment() {
    when(stablishmentRepository.findById(1L)).thenReturn(Optional.of(sede(1L)));
    when(consultorioRepository.existsByStablishmentIdAndCode(1L, "03")).thenReturn(true);

    assertThatThrownBy(() -> consultorioService.create(dtoFor(1L, "03")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("consultorio");

    verify(consultorioRepository, never()).save(any());
  }

  /**
   * El caso que justifica toda la entidad: dos sedes PUEDEN tener su propio
   * "Consultorio 3". La unicidad es por (sede, código), nunca por código solo.
   */
  @Test
  void create_allowsTheSameCode_inADifferentStablishment() {
    when(stablishmentRepository.findById(2L)).thenReturn(Optional.of(sede(2L)));
    when(consultorioRepository.existsByStablishmentIdAndCode(2L, "03")).thenReturn(false);
    when(consultorioRepository.save(any(Consultorio.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ConsultorioDTO created = consultorioService.create(dtoFor(2L, "03"));

    assertThat(created.getCode()).isEqualTo("03");
    assertThat(created.getStablishment().getId()).isEqualTo(2L);
  }

  /**
   * Un consultorio en uso por una plantilla no se puede borrar: la plantilla
   * quedaría apuntando al vacío y la pantalla de sala perdería el dato.
   */
  @Test
  void delete_refuses_whenAScheduleTemplateStillUsesIt() {
    when(consultorioRepository.existsById(5L)).thenReturn(true);
    when(scheduleTemplateRepository.existsByConsultorioId(5L)).thenReturn(true);

    assertThatThrownBy(() -> consultorioService.delete(5L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("horario");

    verify(consultorioRepository, never()).deleteById(any());
  }

  @Test
  void delete_removesConsultorio_whenNoScheduleTemplateUsesIt() {
    when(consultorioRepository.existsById(5L)).thenReturn(true);
    when(scheduleTemplateRepository.existsByConsultorioId(5L)).thenReturn(false);

    consultorioService.delete(5L);

    verify(consultorioRepository).deleteById(5L);
  }
}
