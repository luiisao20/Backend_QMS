package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.devluis.dto.InsurerDTO;
import com.devluis.entity.Insurer;
import com.devluis.repository.CoveragePlanRepository;
import com.devluis.repository.InsurerRepository;
import com.devluis.types.InsurerType;

@ExtendWith(MockitoExtension.class)
class InsurerServiceTest {

  @Mock
  private InsurerRepository insurerRepository;
  @Mock
  private CoveragePlanRepository coveragePlanRepository;

  private InsurerService insurerService;

  @BeforeEach
  void setUp() {
    insurerService = new InsurerService(insurerRepository, coveragePlanRepository);
  }

  private InsurerDTO validDto() {
    return InsurerDTO.builder().name("Seguros Sucre").type(InsurerType.INSURER_PRIVATE).build();
  }

  @Test
  void create_savesAndReturnsTheInsurer() {
    when(insurerRepository.save(any(Insurer.class))).thenAnswer(inv -> {
      Insurer i = inv.getArgument(0);
      i.setId(1L);
      return i;
    });

    InsurerDTO result = insurerService.create(validDto());

    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getName()).isEqualTo("Seguros Sucre");
    assertThat(result.getType()).isEqualTo(InsurerType.INSURER_PRIVATE);
  }

  @Test
  void getAll_withNameFilter_delegatesToFindByNameContainingIgnoreCase() {
    Pageable pageable = PageRequest.of(0, 10);
    Insurer entity = Insurer.builder().id(1L).name("IESS").type(InsurerType.INSURER_PUBLIC).build();
    when(insurerRepository.findByNameContainingIgnoreCase(eq("IESS"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entity)));

    Page<InsurerDTO> result = insurerService.getAll("IESS", pageable);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getType()).isEqualTo(InsurerType.INSURER_PUBLIC);
  }

  @Test
  void getAll_withoutFilter_delegatesToFindAll() {
    Pageable pageable = PageRequest.of(0, 10);
    when(insurerRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

    Page<InsurerDTO> result = insurerService.getAll(pageable);

    assertThat(result.getContent()).isEmpty();
    verify(insurerRepository, never()).findByNameContainingIgnoreCase(any(), any());
  }

  @Test
  void getById_returnsMappedDTO_whenFound() {
    Insurer entity = Insurer.builder().id(1L).name("Seguros Sucre").type(InsurerType.INSURER_PRIVATE).build();
    when(insurerRepository.findById(1L)).thenReturn(Optional.of(entity));

    InsurerDTO result = insurerService.getById(1L);

    assertThat(result.getName()).isEqualTo("Seguros Sucre");
  }

  @Test
  void getById_throws_whenNotFound() {
    when(insurerRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> insurerService.getById(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");
  }

  @Test
  void update_changesNameAndType() {
    Insurer existing = Insurer.builder().id(1L).name("Vieja").type(InsurerType.INSURER_PRIVATE).build();
    when(insurerRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(insurerRepository.save(any(Insurer.class))).thenAnswer(inv -> inv.getArgument(0));

    InsurerDTO result = insurerService.update(1L,
        InsurerDTO.builder().name("Nueva").type(InsurerType.INSURER_PUBLIC).build());

    assertThat(result.getName()).isEqualTo("Nueva");
    assertThat(result.getType()).isEqualTo(InsurerType.INSURER_PUBLIC);
  }

  @Test
  void update_throws_whenNotFound() {
    when(insurerRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> insurerService.update(404L, validDto()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");
  }

  @Test
  void delete_removesInsurer_whenNoPlansReferenceIt() {
    when(insurerRepository.existsById(1L)).thenReturn(true);
    when(coveragePlanRepository.existsByInsurerId(1L)).thenReturn(false);

    insurerService.delete(1L);

    verify(insurerRepository).deleteById(1L);
  }

  @Test
  void delete_throws_whenNotFound() {
    when(insurerRepository.existsById(404L)).thenReturn(false);

    assertThatThrownBy(() -> insurerService.delete(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");

    verify(insurerRepository, never()).deleteById(any());
  }

  @Test
  void delete_throws_whenCoveragePlansStillReferenceIt() {
    when(insurerRepository.existsById(1L)).thenReturn(true);
    when(coveragePlanRepository.existsByInsurerId(1L)).thenReturn(true);

    assertThatThrownBy(() -> insurerService.delete(1L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("planes de cobertura");

    verify(insurerRepository, never()).deleteById(any());
  }
}
