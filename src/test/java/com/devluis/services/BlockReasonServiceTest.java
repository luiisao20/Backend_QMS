package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.devluis.dto.BlockReasonDTO;
import com.devluis.entity.BlockReason;
import com.devluis.repository.BlockReasonRepository;
import com.devluis.repository.HolidayRepository;
import com.devluis.repository.TimeOffRepository;

@ExtendWith(MockitoExtension.class)
class BlockReasonServiceTest {

  @Mock
  private BlockReasonRepository blockReasonRepository;
  @Mock
  private HolidayRepository holidayRepository;
  @Mock
  private TimeOffRepository timeOffRepository;

  private BlockReasonService blockReasonService;

  @BeforeEach
  void setUp() {
    blockReasonService = new BlockReasonService(blockReasonRepository, holidayRepository, timeOffRepository);
  }

  @Test
  void create_savesAndReturnsTheReason() {
    BlockReasonDTO dto = BlockReasonDTO.builder().description("Feriado nacional").build();
    BlockReason saved = BlockReason.builder().id(1L).description("Feriado nacional").build();
    when(blockReasonRepository.save(any(BlockReason.class))).thenReturn(saved);

    BlockReasonDTO result = blockReasonService.create(dto);

    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getDescription()).isEqualTo("Feriado nacional");
  }

  @Test
  void getById_returnsMappedDTO_whenFound() {
    BlockReason entity = BlockReason.builder().id(2L).description("Vacaciones").build();
    when(blockReasonRepository.findById(2L)).thenReturn(Optional.of(entity));

    BlockReasonDTO result = blockReasonService.getById(2L);

    assertThat(result.getDescription()).isEqualTo("Vacaciones");
  }

  @Test
  void getById_throwsClearSpanishMessage_whenNotFound() {
    when(blockReasonRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> blockReasonService.getById(99L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrado");
  }

  @Test
  void getAll_withoutFilter_mapsEveryEntityInThePage() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<BlockReason> page = new PageImpl<>(
        List.of(BlockReason.builder().id(1L).description("Feriado nacional").build()));
    when(blockReasonRepository.findAll(pageable)).thenReturn(page);

    Page<BlockReasonDTO> result = blockReasonService.getAll(pageable);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getDescription()).isEqualTo("Feriado nacional");
  }

  @Test
  void getAll_withDescriptionFilter_delegatesToContainingIgnoreCase() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<BlockReason> page = new PageImpl<>(
        List.of(BlockReason.builder().id(4L).description("Permiso médico").build()));
    when(blockReasonRepository.findByDescriptionContainingIgnoreCase("permiso", pageable)).thenReturn(page);

    Page<BlockReasonDTO> result = blockReasonService.getAll("permiso", pageable);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getDescription()).isEqualTo("Permiso médico");
  }

  @Test
  void update_changesDescription() {
    BlockReason existing = BlockReason.builder().id(3L).description("Antiguo").build();
    when(blockReasonRepository.findById(3L)).thenReturn(Optional.of(existing));
    when(blockReasonRepository.save(any(BlockReason.class))).thenAnswer(inv -> inv.getArgument(0));

    BlockReasonDTO dto = BlockReasonDTO.builder().description("Nuevo").build();
    BlockReasonDTO result = blockReasonService.update(3L, dto);

    assertThat(result.getDescription()).isEqualTo("Nuevo");
  }

  @Test
  void update_throws_whenReasonDoesNotExist() {
    when(blockReasonRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> blockReasonService.update(99L, BlockReasonDTO.builder().description("X").build()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrado");

    verify(blockReasonRepository, never()).save(any());
  }

  @Test
  void delete_removesReason_whenNotReferenced() {
    when(blockReasonRepository.existsById(1L)).thenReturn(true);
    when(holidayRepository.existsByReasonId(1L)).thenReturn(false);
    when(timeOffRepository.existsByReasonId(1L)).thenReturn(false);

    blockReasonService.delete(1L);

    verify(blockReasonRepository).deleteById(1L);
  }

  @Test
  void delete_throwsClearSpanishMessage_whenReferencedByAHoliday() {
    when(blockReasonRepository.existsById(1L)).thenReturn(true);
    when(holidayRepository.existsByReasonId(1L)).thenReturn(true);

    assertThatThrownBy(() -> blockReasonService.delete(1L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("feriado");

    verify(blockReasonRepository, never()).deleteById(any());
  }

  @Test
  void delete_throwsClearSpanishMessage_whenReferencedByATimeOff() {
    when(blockReasonRepository.existsById(1L)).thenReturn(true);
    when(holidayRepository.existsByReasonId(1L)).thenReturn(false);
    when(timeOffRepository.existsByReasonId(1L)).thenReturn(true);

    assertThatThrownBy(() -> blockReasonService.delete(1L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("ausencia");

    verify(blockReasonRepository, never()).deleteById(any());
  }

  @Test
  void delete_throws_whenReasonDoesNotExist() {
    when(blockReasonRepository.existsById(99L)).thenReturn(false);

    assertThatThrownBy(() -> blockReasonService.delete(99L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrado");

    verify(blockReasonRepository, never()).deleteById(any());
  }
}
