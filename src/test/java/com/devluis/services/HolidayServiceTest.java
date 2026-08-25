package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import com.devluis.dto.HolidayDTO;
import com.devluis.dto.StablishmentDTO;
import com.devluis.entity.BlockReason;
import com.devluis.entity.Holiday;
import com.devluis.entity.Schedule;
import com.devluis.entity.Stablishment;
import com.devluis.repository.BlockReasonRepository;
import com.devluis.repository.HolidayRepository;
import com.devluis.repository.ScheduleRepository;
import com.devluis.repository.StablishmentRepository;
import com.devluis.types.ScheduleStatus;

@ExtendWith(MockitoExtension.class)
class HolidayServiceTest {

  @Mock
  private HolidayRepository holidayRepository;
  @Mock
  private BlockReasonRepository blockReasonRepository;
  @Mock
  private StablishmentRepository stablishmentRepository;
  @Mock
  private ScheduleRepository scheduleRepository;
  @Mock
  private ScheduleBlockingSupport scheduleBlockingSupport;

  private HolidayService holidayService;

  @BeforeEach
  void setUp() {
    holidayService = new HolidayService(
        holidayRepository, blockReasonRepository, stablishmentRepository, scheduleRepository, scheduleBlockingSupport);
  }

  private BlockReason reason(long id) {
    return BlockReason.builder().id(id).description("Feriado nacional").build();
  }

  @Test
  void create_savesGlobalHoliday_whenStablishmentIsOmitted() {
    LocalDate date = LocalDate.of(2026, 12, 25);
    HolidayDTO dto = HolidayDTO.builder()
        .date(date)
        .description("Navidad")
        .reason(BlockReasonDTO.builder().id(1L).build())
        .build();

    when(blockReasonRepository.findById(1L)).thenReturn(Optional.of(reason(1L)));
    Schedule affected = Schedule.builder().id(10L).status(ScheduleStatus.STATUS_FREE).build();
    when(scheduleRepository.findByDate(date)).thenReturn(List.of(affected));
    when(scheduleBlockingSupport.blockFreeSchedulesAndReportConflicts(List.of(affected))).thenReturn(List.of());
    when(holidayRepository.save(any(Holiday.class))).thenAnswer(inv -> {
      Holiday h = inv.getArgument(0);
      h.setId(100L);
      return h;
    });

    HolidayDTO result = holidayService.create(dto);

    assertThat(result.getId()).isEqualTo(100L);
    assertThat(result.getStablishment()).isNull();
    assertThat(result.getConflictingScheduleIds()).isEmpty();
    verify(scheduleRepository, never()).findByDateAndStablishmentId(any(), any());
  }

  @Test
  void create_savesStablishmentScopedHoliday_andReportsOccupiedSchedulesAsConflicts() {
    LocalDate date = LocalDate.of(2026, 9, 10);
    Stablishment stablishment = Stablishment.builder().id(5L).name("Sede Norte").address("Av. Siempre Viva").build();
    HolidayDTO dto = HolidayDTO.builder()
        .date(date)
        .description("Aniversario de la sede")
        .stablishment(StablishmentDTO.builder().id(5L).build())
        .reason(BlockReasonDTO.builder().id(2L).build())
        .build();

    when(stablishmentRepository.findById(5L)).thenReturn(Optional.of(stablishment));
    when(blockReasonRepository.findById(2L)).thenReturn(Optional.of(reason(2L)));
    Schedule occupied = Schedule.builder().id(20L).status(ScheduleStatus.STATUS_OCCUPIED).build();
    when(scheduleRepository.findByDateAndStablishmentId(date, 5L)).thenReturn(List.of(occupied));
    when(scheduleBlockingSupport.blockFreeSchedulesAndReportConflicts(List.of(occupied))).thenReturn(List.of(20L));
    when(holidayRepository.save(any(Holiday.class))).thenAnswer(inv -> {
      Holiday h = inv.getArgument(0);
      h.setId(101L);
      return h;
    });

    HolidayDTO result = holidayService.create(dto);

    assertThat(result.getStablishment().getId()).isEqualTo(5L);
    assertThat(result.getConflictingScheduleIds()).containsExactly(20L);
    verify(scheduleRepository, never()).findByDate(any());
  }

  @Test
  void create_throws_whenReasonNotFound() {
    HolidayDTO dto = HolidayDTO.builder()
        .date(LocalDate.of(2026, 1, 1))
        .description("Año nuevo")
        .reason(BlockReasonDTO.builder().id(99L).build())
        .build();
    when(blockReasonRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> holidayService.create(dto))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Motivo no encontrado");

    verify(holidayRepository, never()).save(any());
  }

  @Test
  void create_throws_whenStablishmentNotFound() {
    HolidayDTO dto = HolidayDTO.builder()
        .date(LocalDate.of(2026, 1, 1))
        .description("Feriado local")
        .stablishment(StablishmentDTO.builder().id(404L).build())
        .reason(BlockReasonDTO.builder().id(1L).build())
        .build();
    when(stablishmentRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> holidayService.create(dto))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Establecimiento no encontrado");

    verify(holidayRepository, never()).save(any());
  }

  @Test
  void getById_returnsMappedDTO_whenFound() {
    Holiday entity = Holiday.builder().id(7L).date(LocalDate.of(2026, 5, 1)).description("Día del trabajo")
        .reason(reason(1L)).build();
    when(holidayRepository.findById(7L)).thenReturn(Optional.of(entity));

    HolidayDTO result = holidayService.getById(7L);

    assertThat(result.getDescription()).isEqualTo("Día del trabajo");
    assertThat(result.getReason().getId()).isEqualTo(1L);
  }

  @Test
  void getById_throws_whenNotFound() {
    when(holidayRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> holidayService.getById(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrado");
  }

  @Test
  void getAll_withStablishmentFilter_delegatesToFindByStablishmentId() {
    Pageable pageable = PageRequest.of(0, 10);
    Holiday entity = Holiday.builder().id(1L).date(LocalDate.of(2026, 3, 3)).description("X").reason(reason(1L)).build();
    when(holidayRepository.findByStablishmentId(eq(5L), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entity)));

    Page<HolidayDTO> result = holidayService.getAll(5L, pageable);

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  void update_changesFieldsAndReSweepsForConflicts() {
    LocalDate newDate = LocalDate.of(2026, 11, 2);
    Holiday existing = Holiday.builder().id(8L).date(LocalDate.of(2026, 11, 1)).description("Old")
        .reason(reason(1L)).build();
    when(holidayRepository.findById(8L)).thenReturn(Optional.of(existing));
    when(blockReasonRepository.findById(1L)).thenReturn(Optional.of(reason(1L)));
    when(scheduleRepository.findByDate(newDate)).thenReturn(List.of());
    when(scheduleBlockingSupport.blockFreeSchedulesAndReportConflicts(List.of())).thenReturn(List.of());
    when(holidayRepository.save(any(Holiday.class))).thenAnswer(inv -> inv.getArgument(0));

    HolidayDTO dto = HolidayDTO.builder()
        .date(newDate)
        .description("Feriado puente")
        .reason(BlockReasonDTO.builder().id(1L).build())
        .build();

    HolidayDTO result = holidayService.update(8L, dto);

    assertThat(result.getDate()).isEqualTo(newDate);
    assertThat(result.getDescription()).isEqualTo("Feriado puente");
  }

  @Test
  void delete_removesHoliday_whenExists() {
    when(holidayRepository.existsById(1L)).thenReturn(true);

    holidayService.delete(1L);

    verify(holidayRepository).deleteById(1L);
  }

  @Test
  void delete_throws_whenNotFound() {
    when(holidayRepository.existsById(404L)).thenReturn(false);

    assertThatThrownBy(() -> holidayService.delete(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrado");

    verify(holidayRepository, never()).deleteById(any());
  }
}
