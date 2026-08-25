package com.devluis.services;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.devluis.dto.DateStatusCountRow;
import com.devluis.dto.DayTurnsDTO;
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
import com.devluis.dto.TurnStatusBreakdownDTO;
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

import lombok.Data;

/**
 * Read-only aggregates for the admin panel's metrics/dashboard screens
 * (dashboard/resumen, dashboard/analytics, metricas/*, reportes/general).
 *
 * <p>Every count here is computed by the database via GROUP BY/COUNT queries
 * on TurnRepository/ScheduleRepository/StablishmentRepository/
 * PatientRepository (see those interfaces). This class only reshapes the
 * already-aggregated, small result sets (bounded by days, statuses,
 * establishments, doctors or operators — never by turn volume) into the
 * response DTOs; it never loops over raw Turn/Schedule rows.
 *
 * <p><b>Design decision — Schedule.status is not used for occupancy.</b> The
 * booking flow (TurnService.create/createByStaff) never transitions
 * Schedule.status away from STATUS_FREE, and cancelling a turn never resets
 * it either — so Schedule.status is effectively dead data today. Occupancy
 * is computed instead from Turn existence: a schedule counts as "occupied"
 * if it has at least one non-cancelled turn.
 *
 * <p><b>Design decision — no-shows.</b> TurnStatus has no explicit no-show
 * state. A no-show is derived as: still TURN_PENDING (the patient never
 * checked in) with a schedule date that has already passed.
 */
@Service
@Data
public class MetricsService {

  private static final int DEFAULT_RANGE_DAYS = 30;

  private final TurnRepository turnRepository;
  private final ScheduleRepository scheduleRepository;
  private final PatientRepository patientRepository;
  private final DoctorRepository doctorRepository;
  private final OperatorRepository operatorRepository;
  private final StablishmentRepository stablishmentRepository;
  private final ServiceRepository serviceRepository;

  public MetricsSummaryDTO getSummary() {
    LocalDate today = LocalDate.now();
    TurnStatusBreakdownDTO turnsToday = buildBreakdown(turnRepository.countByStatusForDate(today));

    return MetricsSummaryDTO.builder()
        .turnsToday(turnsToday)
        .totalPatients(patientRepository.count())
        .totalDoctors(doctorRepository.count())
        .totalOperators(operatorRepository.count())
        .totalEstablishments(stablishmentRepository.count())
        .totalServices(serviceRepository.count())
        .build();
  }

  public TurnsSeriesDTO getTurnsSeries(LocalDate from, LocalDate to, Long stablishmentId, Long serviceId) {
    DateRange range = resolveRange(from, to);

    List<DateStatusCountRow> rows = turnRepository.countByDayAndStatus(
        range.from(), range.to(), stablishmentId, serviceId);

    Map<LocalDate, Map<TurnStatus, Long>> byDay = new LinkedHashMap<>();
    for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
      byDay.put(day, zeroFilledStatusMap());
    }
    for (DateStatusCountRow row : rows) {
      byDay.get(row.getDate()).put(row.getStatus(), row.getTotal());
    }

    List<DayTurnsDTO> days = byDay.entrySet().stream()
        .map(entry -> DayTurnsDTO.builder()
            .date(entry.getKey())
            .turns(toBreakdown(entry.getValue()))
            .build())
        .toList();

    return TurnsSeriesDTO.builder()
        .from(range.from())
        .to(range.to())
        .stablishmentId(stablishmentId)
        .serviceId(serviceId)
        .days(days)
        .build();
  }

  public EstablishmentsMetricsDTO getEstablishmentMetrics(LocalDate from, LocalDate to) {
    DateRange range = resolveRange(from, to);

    List<Stablishment> establishments = stablishmentRepository.findAll();

    Map<Long, Long> doctorsCountByEstablishment = toLongMap(stablishmentRepository.countDoctorsPerStablishment());
    Map<Long, Long> servicesCountByEstablishment = toLongMap(stablishmentRepository.countServicesPerStablishment());
    Map<Long, Long> totalSlotsByEstablishment = toLongMap(
        scheduleRepository.countTotalSlotsByStablishmentInRange(range.from(), range.to()));
    Map<Long, Long> occupiedSlotsByEstablishment = toLongMap(
        scheduleRepository.countOccupiedSlotsByStablishmentInRange(range.from(), range.to(), TurnStatus.TURN_CANCELLED));

    Map<Long, Map<TurnStatus, Long>> statusesByEstablishment = new HashMap<>();
    for (LongStatusCountRow row : turnRepository.countByStablishmentAndStatusInRange(range.from(), range.to())) {
      statusesByEstablishment.computeIfAbsent(row.getId(), key -> zeroFilledStatusMap())
          .put(row.getStatus(), row.getTotal());
    }

    List<EstablishmentMetricsDTO> result = establishments.stream()
        .map(establishment -> {
          long totalSlots = totalSlotsByEstablishment.getOrDefault(establishment.getId(), 0L);
          long occupiedSlots = occupiedSlotsByEstablishment.getOrDefault(establishment.getId(), 0L);
          double occupancyRate = totalSlots == 0 ? 0.0 : (double) occupiedSlots / totalSlots;
          Map<TurnStatus, Long> statuses = statusesByEstablishment.getOrDefault(
              establishment.getId(), zeroFilledStatusMap());

          return EstablishmentMetricsDTO.builder()
              .stablishmentId(establishment.getId())
              .name(establishment.getName())
              .doctorsCount(doctorsCountByEstablishment.getOrDefault(establishment.getId(), 0L))
              .servicesCount(servicesCountByEstablishment.getOrDefault(establishment.getId(), 0L))
              .turns(toBreakdown(statuses))
              .totalSlots(totalSlots)
              .occupiedSlots(occupiedSlots)
              .occupancyRate(occupancyRate)
              .build();
        })
        .toList();

    return EstablishmentsMetricsDTO.builder()
        .from(range.from())
        .to(range.to())
        .establishments(result)
        .build();
  }

  public EmployeesMetricsDTO getEmployeesMetrics(LocalDate from, LocalDate to) {
    DateRange range = resolveRange(from, to);
    LocalDate today = LocalDate.now();

    Map<UUID, Map<TurnStatus, Long>> statusesByDoctor = new HashMap<>();
    for (UuidStatusCountRow row : turnRepository.countByDoctorAndStatusInRange(range.from(), range.to())) {
      statusesByDoctor.computeIfAbsent(row.getId(), key -> zeroFilledStatusMap()).put(row.getStatus(), row.getTotal());
    }

    Map<UUID, Long> noShowsByDoctor = turnRepository
        .countByDoctorWithStatusBeforeDate(range.from(), range.to(), today, TurnStatus.TURN_PENDING)
        .stream()
        .collect(Collectors.toMap(UuidCountRow::getId, UuidCountRow::getTotal));

    List<DoctorMetricsDTO> doctors = doctorRepository.findAll().stream()
        .map(doctor -> buildDoctorMetrics(doctor, statusesByDoctor, noShowsByDoctor))
        .toList();

    Map<UUID, Map<TurnStatus, Long>> statusesByOperator = new HashMap<>();
    for (UuidStatusCountRow row : turnRepository.countByOperatorAndStatusInRange(range.from(), range.to())) {
      statusesByOperator.computeIfAbsent(row.getId(), key -> zeroFilledStatusMap()).put(row.getStatus(), row.getTotal());
    }

    List<OperatorMetricsDTO> operators = operatorRepository.findAll().stream()
        .map(operator -> buildOperatorMetrics(operator, statusesByOperator))
        .toList();

    return EmployeesMetricsDTO.builder()
        .from(range.from())
        .to(range.to())
        .doctors(doctors)
        .operators(operators)
        .build();
  }

  public PatientsMetricsDTO getPatientsMetrics(LocalDate from, LocalDate to) {
    DateRange range = resolveRange(from, to);

    OffsetDateTime fromInclusive = range.from().atStartOfDay().atOffset(ZoneOffset.UTC);
    OffsetDateTime toExclusive = range.to().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
    long newPatients = patientRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(fromInclusive, toExclusive);

    Map<TurnStatus, Long> statuses = zeroFilledStatusMap();
    for (StatusCountRow row : turnRepository.countByStatusInRange(range.from(), range.to())) {
      statuses.put(row.getStatus(), row.getTotal());
    }
    long turnsInPeriod = sum(statuses);
    long cancelled = statuses.getOrDefault(TurnStatus.TURN_CANCELLED, 0L);
    double cancellationRate = turnsInPeriod == 0 ? 0.0 : (double) cancelled / turnsInPeriod;

    return PatientsMetricsDTO.builder()
        .from(range.from())
        .to(range.to())
        .newPatients(newPatients)
        .turnsInPeriod(turnsInPeriod)
        .cancelledInPeriod(cancelled)
        .cancellationRate(cancellationRate)
        .build();
  }

  // -- helpers ---------------------------------------------------------------

  private DoctorMetricsDTO buildDoctorMetrics(
      Doctor doctor, Map<UUID, Map<TurnStatus, Long>> statusesByDoctor, Map<UUID, Long> noShowsByDoctor) {
    Map<TurnStatus, Long> statuses = statusesByDoctor.getOrDefault(doctor.getUuid(), zeroFilledStatusMap());

    return DoctorMetricsDTO.builder()
        .doctorId(doctor.getUuid())
        .firstName(doctor.getFirstName())
        .lastName(doctor.getLastName())
        .speciality(doctor.getSpeciality())
        .attended(statuses.getOrDefault(TurnStatus.TURN_TREATED, 0L))
        .cancelled(statuses.getOrDefault(TurnStatus.TURN_CANCELLED, 0L))
        .noShows(noShowsByDoctor.getOrDefault(doctor.getUuid(), 0L))
        .build();
  }

  private OperatorMetricsDTO buildOperatorMetrics(Operator operator, Map<UUID, Map<TurnStatus, Long>> statusesByOperator) {
    Map<TurnStatus, Long> statuses = statusesByOperator.getOrDefault(operator.getUuid(), zeroFilledStatusMap());

    return OperatorMetricsDTO.builder()
        .operatorId(operator.getUuid())
        .firstName(operator.getFirstName())
        .lastName(operator.getLastName())
        .turnsHandled(sum(statuses))
        .cancelled(statuses.getOrDefault(TurnStatus.TURN_CANCELLED, 0L))
        .build();
  }

  // Resolves the effective [from, to] window: an absent `to` defaults to
  // today, an absent `from` defaults to DEFAULT_RANGE_DAYS before the
  // (possibly just-defaulted) `to` — a bounded trailing window instead of
  // scanning all history when the caller passes nothing.
  private DateRange resolveRange(LocalDate from, LocalDate to) {
    LocalDate resolvedTo = to != null ? to : LocalDate.now();
    LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(DEFAULT_RANGE_DAYS - 1L);

    if (resolvedFrom.isAfter(resolvedTo)) {
      throw new RuntimeException("La fecha 'desde' no puede ser posterior a la fecha 'hasta'");
    }

    return new DateRange(resolvedFrom, resolvedTo);
  }

  private Map<TurnStatus, Long> zeroFilledStatusMap() {
    Map<TurnStatus, Long> statuses = new LinkedHashMap<>();
    for (TurnStatus status : TurnStatus.values()) {
      statuses.put(status, 0L);
    }
    return statuses;
  }

  private long sum(Map<TurnStatus, Long> statuses) {
    return statuses.values().stream().mapToLong(Long::longValue).sum();
  }

  private TurnStatusBreakdownDTO toBreakdown(Map<TurnStatus, Long> statuses) {
    return TurnStatusBreakdownDTO.builder().byStatus(statuses).total(sum(statuses)).build();
  }

  private TurnStatusBreakdownDTO buildBreakdown(List<StatusCountRow> rows) {
    Map<TurnStatus, Long> statuses = zeroFilledStatusMap();
    for (StatusCountRow row : rows) {
      statuses.put(row.getStatus(), row.getTotal());
    }
    return toBreakdown(statuses);
  }

  private Map<Long, Long> toLongMap(List<LongCountRow> rows) {
    return rows.stream().collect(Collectors.toMap(LongCountRow::getId, LongCountRow::getTotal));
  }

  private record DateRange(LocalDate from, LocalDate to) {
  }
}
