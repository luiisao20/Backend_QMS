package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devluis.dto.DateStatusCountRow;
import com.devluis.dto.DoctorMetricsDTO;
import com.devluis.dto.EmployeesMetricsDTO;
import com.devluis.dto.EstablishmentMetricsDTO;
import com.devluis.dto.EstablishmentsMetricsDTO;
import com.devluis.dto.LongCountRow;
import com.devluis.dto.LongStatusCountRow;
import com.devluis.dto.MetricsSummaryDTO;
import com.devluis.dto.OperatorMetricsDTO;
import com.devluis.dto.PatientsMetricsDTO;
import com.devluis.dto.StatusCountRow;
import com.devluis.dto.TurnsSeriesDTO;
import com.devluis.dto.UuidCountRow;
import com.devluis.dto.UuidStatusCountRow;
import com.devluis.entity.Doctor;
import com.devluis.entity.Operator;
import com.devluis.entity.Stablishment;
import com.devluis.repository.DoctorRepository;
import com.devluis.repository.OperatorRepository;
import com.devluis.repository.PatientRepository;
import com.devluis.repository.ScheduleRepository;
import com.devluis.repository.ServiceRepository;
import com.devluis.repository.StablishmentRepository;
import com.devluis.repository.TurnRepository;
import com.devluis.types.TurnStatus;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

  @Mock
  private TurnRepository turnRepository;
  @Mock
  private ScheduleRepository scheduleRepository;
  @Mock
  private PatientRepository patientRepository;
  @Mock
  private DoctorRepository doctorRepository;
  @Mock
  private OperatorRepository operatorRepository;
  @Mock
  private StablishmentRepository stablishmentRepository;
  @Mock
  private ServiceRepository serviceRepository;

  private MetricsService metricsService;

  @BeforeEach
  void setUp() {
    metricsService = new MetricsService(
        turnRepository, scheduleRepository, patientRepository, doctorRepository,
        operatorRepository, stablishmentRepository, serviceRepository);
  }

  // -- getSummary --------------------------------------------------------

  @Test
  void getSummary_zeroFillsEveryStatus_andReportsTotalsFromEachRepository() {
    LocalDate today = LocalDate.now();
    when(turnRepository.countByStatusForDate(today)).thenReturn(List.of(
        StatusCountRow.builder().status(TurnStatus.TURN_TREATED).total(3L).build(),
        StatusCountRow.builder().status(TurnStatus.TURN_CANCELLED).total(1L).build()));
    when(patientRepository.count()).thenReturn(340L);
    when(doctorRepository.count()).thenReturn(12L);
    when(operatorRepository.count()).thenReturn(8L);
    when(stablishmentRepository.count()).thenReturn(3L);
    when(serviceRepository.count()).thenReturn(15L);

    MetricsSummaryDTO dto = metricsService.getSummary();

    assertThat(dto.getTurnsToday().getByStatus()).hasSize(TurnStatus.values().length);
    assertThat(dto.getTurnsToday().getByStatus().get(TurnStatus.TURN_TREATED)).isEqualTo(3L);
    assertThat(dto.getTurnsToday().getByStatus().get(TurnStatus.TURN_CANCELLED)).isEqualTo(1L);
    assertThat(dto.getTurnsToday().getByStatus().get(TurnStatus.TURN_PENDING)).isEqualTo(0L);
    assertThat(dto.getTurnsToday().getTotal()).isEqualTo(4L);
    assertThat(dto.getTotalPatients()).isEqualTo(340L);
    assertThat(dto.getTotalDoctors()).isEqualTo(12L);
    assertThat(dto.getTotalOperators()).isEqualTo(8L);
    assertThat(dto.getTotalEstablishments()).isEqualTo(3L);
    assertThat(dto.getTotalServices()).isEqualTo(15L);
  }

  // -- getTurnsSeries: date defaulting -------------------------------------

  @Test
  void getTurnsSeries_defaultsToA30DayWindowEndingToday_whenFromAndToAreNull() {
    when(turnRepository.countByDayAndStatus(any(), any(), any(), any())).thenReturn(List.of());

    TurnsSeriesDTO dto = metricsService.getTurnsSeries(null, null, null, null);

    LocalDate expectedTo = LocalDate.now();
    LocalDate expectedFrom = expectedTo.minusDays(29);
    assertThat(dto.getTo()).isEqualTo(expectedTo);
    assertThat(dto.getFrom()).isEqualTo(expectedFrom);
    assertThat(dto.getDays()).hasSize(30);
  }

  @Test
  void getTurnsSeries_defaultsOnlyTheMissingBound_whenOnlyOneIsGiven() {
    when(turnRepository.countByDayAndStatus(any(), any(), any(), any())).thenReturn(List.of());
    LocalDate explicitFrom = LocalDate.of(2026, 8, 1);

    TurnsSeriesDTO dto = metricsService.getTurnsSeries(explicitFrom, null, null, null);

    assertThat(dto.getFrom()).isEqualTo(explicitFrom);
    assertThat(dto.getTo()).isEqualTo(LocalDate.now());
  }

  @Test
  void getTurnsSeries_rejectsFromAfterTo_withSpanishMessage() {
    LocalDate from = LocalDate.of(2026, 8, 10);
    LocalDate to = LocalDate.of(2026, 8, 1);

    assertThatThrownBy(() -> metricsService.getTurnsSeries(from, to, null, null))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no puede ser posterior");
  }

  @Test
  void getTurnsSeries_zeroFillsDaysWithNoData_andAppliesRowsToTheRightDay() {
    LocalDate from = LocalDate.of(2026, 8, 1);
    LocalDate to = LocalDate.of(2026, 8, 3);
    when(turnRepository.countByDayAndStatus(from, to, null, null)).thenReturn(List.of(
        DateStatusCountRow.builder().date(LocalDate.of(2026, 8, 2)).status(TurnStatus.TURN_TREATED).total(2L).build()));

    TurnsSeriesDTO dto = metricsService.getTurnsSeries(from, to, null, null);

    assertThat(dto.getDays()).hasSize(3);
    assertThat(dto.getDays().get(0).getTurns().getTotal()).isEqualTo(0L);
    assertThat(dto.getDays().get(1).getDate()).isEqualTo(LocalDate.of(2026, 8, 2));
    assertThat(dto.getDays().get(1).getTurns().getByStatus().get(TurnStatus.TURN_TREATED)).isEqualTo(2L);
    assertThat(dto.getDays().get(1).getTurns().getTotal()).isEqualTo(2L);
    assertThat(dto.getDays().get(2).getTurns().getTotal()).isEqualTo(0L);
  }

  @Test
  void getTurnsSeries_passesStablishmentAndServiceFiltersThroughToTheRepository() {
    LocalDate from = LocalDate.of(2026, 8, 1);
    LocalDate to = LocalDate.of(2026, 8, 1);
    when(turnRepository.countByDayAndStatus(from, to, 5L, 10L)).thenReturn(List.of());

    TurnsSeriesDTO dto = metricsService.getTurnsSeries(from, to, 5L, 10L);

    assertThat(dto.getStablishmentId()).isEqualTo(5L);
    assertThat(dto.getServiceId()).isEqualTo(10L);
    verify(turnRepository).countByDayAndStatus(from, to, 5L, 10L);
  }

  // -- getEstablishmentMetrics ---------------------------------------------

  @Test
  void getEstablishmentMetrics_computesOccupancyRate_andIncludesEstablishmentsWithZeroActivity() {
    Stablishment busy = Stablishment.builder().id(1L).name("Sede Norte").address("Av. Norte 1").build();
    Stablishment idle = Stablishment.builder().id(2L).name("Sede Sur").address("Av. Sur 1").build();
    when(stablishmentRepository.findAll()).thenReturn(List.of(busy, idle));
    when(stablishmentRepository.countDoctorsPerStablishment())
        .thenReturn(List.of(LongCountRow.builder().id(1L).total(6L).build()));
    when(stablishmentRepository.countServicesPerStablishment())
        .thenReturn(List.of(LongCountRow.builder().id(1L).total(4L).build()));
    when(turnRepository.countByStablishmentAndStatusInRange(any(), any())).thenReturn(List.of(
        LongStatusCountRow.builder().id(1L).status(TurnStatus.TURN_TREATED).total(4L).build()));
    when(scheduleRepository.countTotalSlotsByStablishmentInRange(any(), any()))
        .thenReturn(List.of(LongCountRow.builder().id(1L).total(10L).build()));
    when(scheduleRepository.countOccupiedSlotsByStablishmentInRange(any(), any(), eq(TurnStatus.TURN_CANCELLED)))
        .thenReturn(List.of(LongCountRow.builder().id(1L).total(4L).build()));

    EstablishmentsMetricsDTO dto = metricsService.getEstablishmentMetrics(null, null);

    assertThat(dto.getEstablishments()).hasSize(2);
    EstablishmentMetricsDTO busyDto = dto.getEstablishments().stream()
        .filter(e -> e.getStablishmentId().equals(1L)).findFirst().orElseThrow();
    assertThat(busyDto.getDoctorsCount()).isEqualTo(6L);
    assertThat(busyDto.getServicesCount()).isEqualTo(4L);
    assertThat(busyDto.getTotalSlots()).isEqualTo(10L);
    assertThat(busyDto.getOccupiedSlots()).isEqualTo(4L);
    assertThat(busyDto.getOccupancyRate()).isEqualTo(0.4);
    assertThat(busyDto.getTurns().getByStatus().get(TurnStatus.TURN_TREATED)).isEqualTo(4L);

    EstablishmentMetricsDTO idleDto = dto.getEstablishments().stream()
        .filter(e -> e.getStablishmentId().equals(2L)).findFirst().orElseThrow();
    assertThat(idleDto.getDoctorsCount()).isEqualTo(0L);
    assertThat(idleDto.getTotalSlots()).isEqualTo(0L);
    assertThat(idleDto.getOccupancyRate()).isEqualTo(0.0);
  }

  // -- getEmployeesMetrics ---------------------------------------------------

  @Test
  void getEmployeesMetrics_computesAttendedCancelledAndNoShows_perDoctor() {
    UUID doctorUuid = UUID.randomUUID();
    Doctor doctor = Doctor.builder().uuid(doctorUuid).firstName("Carla").lastName("Mendez").speciality("Cardiologia").build();
    when(doctorRepository.findAll()).thenReturn(List.of(doctor));
    when(operatorRepository.findAll()).thenReturn(List.of());
    when(turnRepository.countByDoctorAndStatusInRange(any(), any())).thenReturn(List.of(
        UuidStatusCountRow.builder().id(doctorUuid).status(TurnStatus.TURN_TREATED).total(20L).build(),
        UuidStatusCountRow.builder().id(doctorUuid).status(TurnStatus.TURN_CANCELLED).total(3L).build()));
    when(turnRepository.countByDoctorWithStatusBeforeDate(any(), any(), any(), eq(TurnStatus.TURN_PENDING)))
        .thenReturn(List.of(UuidCountRow.builder().id(doctorUuid).total(2L).build()));
    when(turnRepository.countByOperatorAndStatusInRange(any(), any())).thenReturn(List.of());

    EmployeesMetricsDTO dto = metricsService.getEmployeesMetrics(null, null);

    assertThat(dto.getDoctors()).hasSize(1);
    DoctorMetricsDTO doctorDto = dto.getDoctors().get(0);
    assertThat(doctorDto.getDoctorId()).isEqualTo(doctorUuid);
    assertThat(doctorDto.getAttended()).isEqualTo(20L);
    assertThat(doctorDto.getCancelled()).isEqualTo(3L);
    assertThat(doctorDto.getNoShows()).isEqualTo(2L);
  }

  @Test
  void getEmployeesMetrics_includesDoctorsWithZeroActivity() {
    Doctor doctor = Doctor.builder().uuid(UUID.randomUUID()).firstName("Ana").lastName("Ruiz").speciality("Pediatria").build();
    when(doctorRepository.findAll()).thenReturn(List.of(doctor));
    when(operatorRepository.findAll()).thenReturn(List.of());
    when(turnRepository.countByDoctorAndStatusInRange(any(), any())).thenReturn(List.of());
    when(turnRepository.countByDoctorWithStatusBeforeDate(any(), any(), any(), any())).thenReturn(List.of());
    when(turnRepository.countByOperatorAndStatusInRange(any(), any())).thenReturn(List.of());

    EmployeesMetricsDTO dto = metricsService.getEmployeesMetrics(null, null);

    DoctorMetricsDTO doctorDto = dto.getDoctors().get(0);
    assertThat(doctorDto.getAttended()).isZero();
    assertThat(doctorDto.getCancelled()).isZero();
    assertThat(doctorDto.getNoShows()).isZero();
  }

  @Test
  void getEmployeesMetrics_computesTurnsHandledAsSumOfAllStatuses_perOperator() {
    UUID operatorUuid = UUID.randomUUID();
    Operator operator = Operator.builder().uuid(operatorUuid).firstName("Luis").lastName("Peña").build();
    when(doctorRepository.findAll()).thenReturn(List.of());
    when(operatorRepository.findAll()).thenReturn(List.of(operator));
    when(turnRepository.countByDoctorAndStatusInRange(any(), any())).thenReturn(List.of());
    when(turnRepository.countByDoctorWithStatusBeforeDate(any(), any(), any(), any())).thenReturn(List.of());
    when(turnRepository.countByOperatorAndStatusInRange(any(), any())).thenReturn(List.of(
        UuidStatusCountRow.builder().id(operatorUuid).status(TurnStatus.TURN_WAITNG).total(9L).build(),
        UuidStatusCountRow.builder().id(operatorUuid).status(TurnStatus.TURN_CANCELLED).total(2L).build()));

    EmployeesMetricsDTO dto = metricsService.getEmployeesMetrics(null, null);

    OperatorMetricsDTO operatorDto = dto.getOperators().get(0);
    assertThat(operatorDto.getOperatorId()).isEqualTo(operatorUuid);
    assertThat(operatorDto.getTurnsHandled()).isEqualTo(11L);
    assertThat(operatorDto.getCancelled()).isEqualTo(2L);
  }

  @Test
  void getEmployeesMetrics_noShowLookup_isCalledWithTodayAndPendingStatus() {
    when(doctorRepository.findAll()).thenReturn(List.of());
    when(operatorRepository.findAll()).thenReturn(List.of());
    when(turnRepository.countByDoctorAndStatusInRange(any(), any())).thenReturn(List.of());
    when(turnRepository.countByDoctorWithStatusBeforeDate(any(), any(), any(), any())).thenReturn(List.of());
    when(turnRepository.countByOperatorAndStatusInRange(any(), any())).thenReturn(List.of());

    metricsService.getEmployeesMetrics(null, null);

    verify(turnRepository).countByDoctorWithStatusBeforeDate(any(), any(), eq(LocalDate.now()), eq(TurnStatus.TURN_PENDING));
  }

  // -- getPatientsMetrics -----------------------------------------------------

  @Test
  void getPatientsMetrics_computesNewPatientsAndCancellationRate() {
    LocalDate from = LocalDate.of(2026, 8, 1);
    LocalDate to = LocalDate.of(2026, 8, 10);
    when(patientRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(7L);
    when(turnRepository.countByStatusInRange(from, to)).thenReturn(List.of(
        StatusCountRow.builder().status(TurnStatus.TURN_TREATED).total(8L).build(),
        StatusCountRow.builder().status(TurnStatus.TURN_CANCELLED).total(2L).build()));

    PatientsMetricsDTO dto = metricsService.getPatientsMetrics(from, to);

    assertThat(dto.getNewPatients()).isEqualTo(7L);
    assertThat(dto.getTurnsInPeriod()).isEqualTo(10L);
    assertThat(dto.getCancelledInPeriod()).isEqualTo(2L);
    assertThat(dto.getCancellationRate()).isEqualTo(0.2);
  }

  @Test
  void getPatientsMetrics_cancellationRateIsZero_whenThereAreNoTurnsInThePeriod() {
    when(patientRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(0L);
    when(turnRepository.countByStatusInRange(any(), any())).thenReturn(List.of());

    PatientsMetricsDTO dto = metricsService.getPatientsMetrics(null, null);

    assertThat(dto.getCancellationRate()).isEqualTo(0.0);
  }

  @Test
  void getPatientsMetrics_convertsTheLocalDateRangeToHalfOpenOffsetDateTimeBoundaries() {
    LocalDate from = LocalDate.of(2026, 8, 1);
    LocalDate to = LocalDate.of(2026, 8, 10);
    when(patientRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(0L);
    when(turnRepository.countByStatusInRange(any(), any())).thenReturn(List.of());

    metricsService.getPatientsMetrics(from, to);

    OffsetDateTime expectedFrom = from.atStartOfDay().atOffset(ZoneOffset.UTC);
    OffsetDateTime expectedToExclusive = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
    verify(patientRepository).countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(expectedFrom, expectedToExclusive);
  }
}
