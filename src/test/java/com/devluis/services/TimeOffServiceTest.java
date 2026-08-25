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
import java.util.UUID;

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
import com.devluis.dto.DoctorDTO;
import com.devluis.dto.TimeOffDTO;
import com.devluis.entity.BlockReason;
import com.devluis.entity.Doctor;
import com.devluis.entity.Schedule;
import com.devluis.entity.TimeOff;
import com.devluis.repository.BlockReasonRepository;
import com.devluis.repository.DoctorRepository;
import com.devluis.repository.ScheduleRepository;
import com.devluis.repository.TimeOffRepository;
import com.devluis.types.ScheduleStatus;
import com.devluis.types.TimeOffKind;

@ExtendWith(MockitoExtension.class)
class TimeOffServiceTest {

  @Mock
  private TimeOffRepository timeOffRepository;
  @Mock
  private DoctorRepository doctorRepository;
  @Mock
  private BlockReasonRepository blockReasonRepository;
  @Mock
  private ScheduleRepository scheduleRepository;
  @Mock
  private ScheduleBlockingSupport scheduleBlockingSupport;

  private TimeOffService timeOffService;

  private final UUID doctorUuid = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    timeOffService = new TimeOffService(
        timeOffRepository, doctorRepository, blockReasonRepository, scheduleRepository, scheduleBlockingSupport);
  }

  private Doctor doctor() {
    return Doctor.builder().uuid(doctorUuid).firstName("Ana").lastName("Pérez").build();
  }

  private BlockReason reason() {
    return BlockReason.builder().id(1L).description("Vacaciones programadas").build();
  }

  private TimeOffDTO validDto(LocalDate start, LocalDate end) {
    return TimeOffDTO.builder()
        .doctor(DoctorDTO.builder().uuid(doctorUuid).build())
        .kind(TimeOffKind.KIND_VACATION)
        .startDate(start)
        .endDate(end)
        .reason(BlockReasonDTO.builder().id(1L).build())
        .build();
  }

  @Test
  void create_savesTimeOff_andBlocksFreeSchedulesInRange() {
    LocalDate start = LocalDate.of(2026, 9, 1);
    LocalDate end = LocalDate.of(2026, 9, 5);
    when(doctorRepository.findById(doctorUuid)).thenReturn(Optional.of(doctor()));
    when(blockReasonRepository.findById(1L)).thenReturn(Optional.of(reason()));
    Schedule free = Schedule.builder().id(30L).status(ScheduleStatus.STATUS_FREE).build();
    when(scheduleRepository.findByDoctorUuidAndDateBetween(doctorUuid, start, end)).thenReturn(List.of(free));
    when(scheduleBlockingSupport.blockFreeSchedulesAndReportConflicts(List.of(free))).thenReturn(List.of());
    when(timeOffRepository.save(any(TimeOff.class))).thenAnswer(inv -> {
      TimeOff t = inv.getArgument(0);
      t.setId(200L);
      return t;
    });

    TimeOffDTO result = timeOffService.create(validDto(start, end));

    assertThat(result.getId()).isEqualTo(200L);
    assertThat(result.getConflictingScheduleIds()).isEmpty();
  }

  @Test
  void create_reportsOccupiedSchedulesAsConflicts_insteadOfDestroyingTheBookedTurn() {
    LocalDate start = LocalDate.of(2026, 10, 1);
    LocalDate end = LocalDate.of(2026, 10, 3);
    when(doctorRepository.findById(doctorUuid)).thenReturn(Optional.of(doctor()));
    when(blockReasonRepository.findById(1L)).thenReturn(Optional.of(reason()));
    Schedule occupied = Schedule.builder().id(31L).status(ScheduleStatus.STATUS_OCCUPIED).build();
    when(scheduleRepository.findByDoctorUuidAndDateBetween(doctorUuid, start, end)).thenReturn(List.of(occupied));
    when(scheduleBlockingSupport.blockFreeSchedulesAndReportConflicts(List.of(occupied))).thenReturn(List.of(31L));
    when(timeOffRepository.save(any(TimeOff.class))).thenAnswer(inv -> inv.getArgument(0));

    TimeOffDTO result = timeOffService.create(validDto(start, end));

    assertThat(result.getConflictingScheduleIds()).containsExactly(31L);
  }

  @Test
  void create_throws_whenEndDateIsBeforeStartDate() {
    when(doctorRepository.findById(doctorUuid)).thenReturn(Optional.of(doctor()));

    TimeOffDTO dto = validDto(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1));

    assertThatThrownBy(() -> timeOffService.create(dto))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("fecha");

    verify(timeOffRepository, never()).save(any());
  }

  @Test
  void create_throws_whenDoctorNotFound() {
    when(doctorRepository.findById(doctorUuid)).thenReturn(Optional.empty());

    TimeOffDTO dto = validDto(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5));

    assertThatThrownBy(() -> timeOffService.create(dto))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Doctor no encontrado");

    verify(timeOffRepository, never()).save(any());
  }

  @Test
  void create_throws_whenReasonNotFound() {
    when(doctorRepository.findById(doctorUuid)).thenReturn(Optional.of(doctor()));
    when(blockReasonRepository.findById(1L)).thenReturn(Optional.empty());

    TimeOffDTO dto = validDto(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5));

    assertThatThrownBy(() -> timeOffService.create(dto))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Motivo no encontrado");

    verify(timeOffRepository, never()).save(any());
  }

  @Test
  void getById_returnsMappedDTO_whenFound() {
    TimeOff entity = TimeOff.builder().id(9L).doctor(doctor()).kind(TimeOffKind.KIND_PERMISSION)
        .startDate(LocalDate.of(2026, 4, 1)).endDate(LocalDate.of(2026, 4, 2)).reason(reason()).build();
    when(timeOffRepository.findById(9L)).thenReturn(Optional.of(entity));

    TimeOffDTO result = timeOffService.getById(9L);

    assertThat(result.getKind()).isEqualTo(TimeOffKind.KIND_PERMISSION);
    assertThat(result.getDoctor().getUuid()).isEqualTo(doctorUuid);
  }

  @Test
  void getById_throws_whenNotFound() {
    when(timeOffRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> timeOffService.getById(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");
  }

  @Test
  void getAll_withDoctorAndKindFilter_delegatesToFindByDoctorUuidAndKind() {
    Pageable pageable = PageRequest.of(0, 10);
    TimeOff entity = TimeOff.builder().id(1L).doctor(doctor()).kind(TimeOffKind.KIND_VACATION)
        .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 5)).reason(reason()).build();
    when(timeOffRepository.findByDoctorUuidAndKind(eq(doctorUuid), eq(TimeOffKind.KIND_VACATION), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entity)));

    Page<TimeOffDTO> result = timeOffService.getAll(doctorUuid, TimeOffKind.KIND_VACATION, pageable);

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  void getAll_withOnlyKindFilter_delegatesToFindByKind() {
    Pageable pageable = PageRequest.of(0, 10);
    when(timeOffRepository.findByKind(eq(TimeOffKind.KIND_PERMISSION), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    Page<TimeOffDTO> result = timeOffService.getAll(null, TimeOffKind.KIND_PERMISSION, pageable);

    assertThat(result.getContent()).isEmpty();
    verify(timeOffRepository, never()).findByDoctorUuidAndKind(any(), any(), any());
  }

  @Test
  void update_changesDateRange_andReSweepsForConflicts() {
    LocalDate newStart = LocalDate.of(2026, 12, 1);
    LocalDate newEnd = LocalDate.of(2026, 12, 10);
    TimeOff existing = TimeOff.builder().id(5L).doctor(doctor()).kind(TimeOffKind.KIND_VACATION)
        .startDate(LocalDate.of(2026, 11, 1)).endDate(LocalDate.of(2026, 11, 5)).reason(reason()).build();
    when(timeOffRepository.findById(5L)).thenReturn(Optional.of(existing));
    when(doctorRepository.findById(doctorUuid)).thenReturn(Optional.of(doctor()));
    when(blockReasonRepository.findById(1L)).thenReturn(Optional.of(reason()));
    when(scheduleRepository.findByDoctorUuidAndDateBetween(doctorUuid, newStart, newEnd)).thenReturn(List.of());
    when(scheduleBlockingSupport.blockFreeSchedulesAndReportConflicts(List.of())).thenReturn(List.of());
    when(timeOffRepository.save(any(TimeOff.class))).thenAnswer(inv -> inv.getArgument(0));

    TimeOffDTO result = timeOffService.update(5L, validDto(newStart, newEnd));

    assertThat(result.getStartDate()).isEqualTo(newStart);
    assertThat(result.getEndDate()).isEqualTo(newEnd);
  }

  @Test
  void delete_removesTimeOff_whenExists() {
    when(timeOffRepository.existsById(1L)).thenReturn(true);

    timeOffService.delete(1L);

    verify(timeOffRepository).deleteById(1L);
  }

  @Test
  void delete_throws_whenNotFound() {
    when(timeOffRepository.existsById(404L)).thenReturn(false);

    assertThatThrownBy(() -> timeOffService.delete(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");

    verify(timeOffRepository, never()).deleteById(any());
  }
}
