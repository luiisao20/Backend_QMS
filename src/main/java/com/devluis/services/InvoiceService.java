package com.devluis.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devluis.dto.CoverageQuoteDTO;
import com.devluis.dto.InvoiceDTO;
import com.devluis.dto.InvoiceLineItemDTO;
import com.devluis.dto.PatientDTO;
import com.devluis.entity.Invoice;
import com.devluis.entity.InvoiceLineItem;
import com.devluis.entity.Patient;
import com.devluis.entity.PatientCoverage;
import com.devluis.entity.Promotion;
import com.devluis.entity.ServicePackage;
import com.devluis.entity.Servicio;
import com.devluis.entity.SessionPlan;
import com.devluis.entity.Turn;
import com.devluis.repository.InvoiceLineItemRepository;
import com.devluis.repository.InvoiceRepository;
import com.devluis.repository.PatientCoverageRepository;
import com.devluis.repository.PatientRepository;
import com.devluis.repository.PromotionRepository;
import com.devluis.repository.ServicePackageRepository;
import com.devluis.repository.SessionPlanRepository;
import com.devluis.repository.TurnRepository;
import com.devluis.types.InvoiceLineSourceType;
import com.devluis.types.InvoiceStatus;
import com.devluis.types.TurnStatus;
import com.devluis.utils.Money;

import lombok.Data;

/**
 * Builds and reads Invoices. This is the ONLY place in the finance group
 * that ever calls {@link CoveragePricingService} — every TURN line's
 * amount/insurerCoveredAmount/patientResponsibleAmount/insurer name are
 * copied from that ONE quote into the {@link InvoiceLineItem} at creation
 * time and never touched again. Every read method below
 * ({@link #getById}/{@link #getForPatient}/{@link #search}) maps the
 * ALREADY-STORED entity straight to a DTO — it does not re-resolve a Turn,
 * Servicio, PatientCoverage or Promotion, so an admin editing any of those
 * later has ZERO effect on an already-issued invoice. See
 * InvoiceLineItemTest... (there is no such class — this invariant is proven
 * in {@code InvoiceServiceTest#getById_lineItemAmounts_areTheStoredSnapshot_andNeverTouchTheLiveCatalog}
 * via {@code verifyNoInteractions} on every catalog/pricing dependency).
 */
@Service
@Data
public class InvoiceService {
  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineItemRepository invoiceLineItemRepository;
  private final PatientRepository patientRepository;
  private final TurnRepository turnRepository;
  private final ServicePackageRepository servicePackageRepository;
  private final SessionPlanRepository sessionPlanRepository;
  private final PatientCoverageRepository patientCoverageRepository;
  private final PromotionRepository promotionRepository;
  private final CoveragePricingService coveragePricingService;
  private final InvoiceAccessGuard invoiceAccessGuard;
  private final PaymentService paymentService;

  // An Invoice is created ATOMICALLY with every one of its lines — see
  // Invoice's own docblock for why there is no incremental "add a line
  // later" endpoint. All TURN lines in this one call share the SAME
  // "currently active coverage" snapshot, resolved ONCE here — this is what
  // guarantees a single invoice can never end up with lines billed under two
  // different insurers (see Claim's docblock).
  @Transactional
  public InvoiceDTO create(InvoiceDTO dto) {
    Patient patient = resolvePatient(dto);
    PatientCoverage activeCoverage = patientCoverageRepository
        .findFirstByPatientUuidAndActiveTrue(patient.getUuid())
        .orElse(null);

    Invoice invoice = Invoice.builder()
        .patient(patient)
        .status(InvoiceStatus.ISSUED)
        .items(new java.util.ArrayList<>())
        .build();

    List<InvoiceLineItem> items = dto.getItems().stream()
        .map(itemDto -> buildLineItem(itemDto, patient, activeCoverage))
        .toList();
    items.forEach(item -> {
      item.setInvoice(invoice);
      invoice.getItems().add(item);
    });

    invoice.setTotal(Money.sum(items.stream().map(InvoiceLineItem::getAmount).toList()));

    Invoice saved = invoiceRepository.save(invoice);
    return mapToDTO(saved);
  }

  public InvoiceDTO getById(Long id, Authentication auth) {
    Invoice invoice = findByIdOrThrow(id);
    invoiceAccessGuard.assertCanAccessInvoice(auth, resolvePatientUuid(invoice));
    return mapToDTO(invoice);
  }

  // Backs both GET /api/invoices/me and the staff
  // GET /api/patients/{patientId}/invoices screen, same one-method-serves-both
  // precedent as PatientCoverageService#listForPatient/EncounterService.
  public Page<InvoiceDTO> getForPatient(UUID patientUuid, Pageable pageable) {
    return invoiceRepository.findByPatientUuid(patientUuid, pageable).map(this::mapToDTO);
  }

  public Page<InvoiceDTO> search(UUID patientUuid, InvoiceStatus status, Pageable pageable) {
    return invoiceRepository.search(patientUuid, status, pageable).map(this::mapToDTO);
  }

  // The only "removal" mechanism — see Invoice's docblock. Refused once the
  // invoice is already fully PAID (no refund process is modelled) or already
  // VOID (no double-void). Reachable from ISSUED or PARTIALLY_PAID.
  @Transactional
  public InvoiceDTO voidInvoice(Long id, String reason, UUID voidedByUuid) {
    Invoice invoice = findByIdOrThrow(id);
    if (invoice.getStatus() == InvoiceStatus.VOID) {
      throw new RuntimeException("La factura ya está anulada");
    }
    if (invoice.getStatus() == InvoiceStatus.PAID) {
      throw new RuntimeException("No se puede anular una factura completamente pagada");
    }

    invoice.setStatus(InvoiceStatus.VOID);
    invoice.setVoidedAt(java.time.OffsetDateTime.now());
    invoice.setVoidReason(reason);
    invoice.setVoidedByUuid(voidedByUuid);

    Invoice updated = invoiceRepository.save(invoice);
    return mapToDTO(updated);
  }

  private InvoiceLineItem buildLineItem(InvoiceLineItemDTO dto, Patient patient, PatientCoverage activeCoverage) {
    if (dto.getSourceType() == null) {
      throw new RuntimeException("El tipo de origen de la línea es obligatorio");
    }
    return switch (dto.getSourceType()) {
      case TURN -> buildTurnLine(dto, patient, activeCoverage);
      case PACKAGE -> buildPackageLine(dto);
      case SESSION_PLAN -> buildSessionPlanLine(dto);
      case FREE_LINE -> buildFreeLine(dto);
    };
  }

  // Only TURN lines can ever be insurer-covered, and only TURN lines are
  // guarded against double-billing: a specific calendar Turn is a one-time
  // scheduling fact (see EncounterService's own TURN_TREATED-only rule for
  // the same reasoning), unlike a ServicePackage/SessionPlan, which is a
  // catalog product this or any other patient may legitimately purchase
  // again later — see buildPackageLine/buildSessionPlanLine.
  private InvoiceLineItem buildTurnLine(InvoiceLineItemDTO dto, Patient patient, PatientCoverage activeCoverage) {
    if (dto.getSourceId() == null) {
      throw new RuntimeException("El turno es obligatorio para una línea de tipo TURN");
    }
    Turn turn = turnRepository.findById(dto.getSourceId())
        .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

    if (turn.getStatus() != TurnStatus.TURN_TREATED) {
      throw new RuntimeException("Solo se puede facturar un turno atendido");
    }
    if (turn.getPatient() == null || !turn.getPatient().getUuid().equals(patient.getUuid())) {
      throw new RuntimeException("El turno no pertenece al paciente de esta factura");
    }
    if (invoiceLineItemRepository.existsBySourceTypeAndSourceIdAndInvoiceStatusNot(
        InvoiceLineSourceType.TURN, turn.getId(), InvoiceStatus.VOID)) {
      throw new RuntimeException("Este turno ya fue facturado");
    }
    if (turn.getSchedule() == null || turn.getSchedule().getService() == null) {
      throw new RuntimeException("El turno no tiene un servicio asociado");
    }

    Servicio servicio = turn.getSchedule().getService();
    LocalDate today = LocalDate.now();
    Promotion activePromotion = promotionRepository
        .findFirstByServicioIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(servicio.getId(), today, today)
        .orElse(null);
    CoverageQuoteDTO quote = coveragePricingService.quote(servicio, activeCoverage, activePromotion);

    return InvoiceLineItem.builder()
        .sourceType(InvoiceLineSourceType.TURN)
        .sourceId(turn.getId())
        .description(servicio.getName())
        .amount(quote.getNetPrice())
        .insurerCoveredAmount(quote.getInsurerCovers())
        .patientResponsibleAmount(quote.getPatientPays())
        .insurerNameSnapshot(quote.isHasCoverage() ? quote.getInsurerName() : null)
        .planNameSnapshot(quote.isHasCoverage() ? quote.getPlanName() : null)
        .build();
  }

  // ServicePackage/SessionPlan deliberately do NOT flow through
  // CoveragePricingService (see their own docblocks) — always 100%
  // patient-responsible.
  private InvoiceLineItem buildPackageLine(InvoiceLineItemDTO dto) {
    if (dto.getSourceId() == null) {
      throw new RuntimeException("El paquete es obligatorio para una línea de tipo PACKAGE");
    }
    ServicePackage servicePackage = servicePackageRepository.findById(dto.getSourceId())
        .orElseThrow(() -> new RuntimeException("Paquete no encontrado"));

    BigDecimal amount = Money.of(servicePackage.getPrice());
    return InvoiceLineItem.builder()
        .sourceType(InvoiceLineSourceType.PACKAGE)
        .sourceId(servicePackage.getId())
        .description("Paquete: " + servicePackage.getName())
        .amount(amount)
        .insurerCoveredAmount(Money.zero())
        .patientResponsibleAmount(amount)
        .build();
  }

  private InvoiceLineItem buildSessionPlanLine(InvoiceLineItemDTO dto) {
    if (dto.getSourceId() == null) {
      throw new RuntimeException("El plan de sesiones es obligatorio para una línea de tipo SESSION_PLAN");
    }
    SessionPlan sessionPlan = sessionPlanRepository.findById(dto.getSourceId())
        .orElseThrow(() -> new RuntimeException("Plan de sesiones no encontrado"));

    BigDecimal amount = Money.of(sessionPlan.getPrice());
    return InvoiceLineItem.builder()
        .sourceType(InvoiceLineSourceType.SESSION_PLAN)
        .sourceId(sessionPlan.getId())
        .description("Plan de sesiones: " + sessionPlan.getName())
        .amount(amount)
        .insurerCoveredAmount(Money.zero())
        .patientResponsibleAmount(amount)
        .build();
  }

  private InvoiceLineItem buildFreeLine(InvoiceLineItemDTO dto) {
    if (dto.getDescription() == null || dto.getDescription().isBlank()) {
      throw new RuntimeException("La descripción es obligatoria para una línea libre");
    }
    if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
      throw new RuntimeException("El monto es obligatorio y debe ser mayor a cero para una línea libre");
    }

    BigDecimal amount = Money.of(dto.getAmount());
    return InvoiceLineItem.builder()
        .sourceType(InvoiceLineSourceType.FREE_LINE)
        .description(dto.getDescription())
        .amount(amount)
        .insurerCoveredAmount(Money.zero())
        .patientResponsibleAmount(amount)
        .build();
  }

  private Invoice findByIdOrThrow(Long id) {
    return invoiceRepository.findById(id).orElseThrow(() -> new RuntimeException("Factura no encontrada"));
  }

  private Patient resolvePatient(InvoiceDTO dto) {
    return patientRepository.findById(dto.getPatient().getUuid())
        .orElseThrow(() -> new RuntimeException("Paciente no encontrado"));
  }

  private UUID resolvePatientUuid(Invoice invoice) {
    return invoice.getPatient() != null ? invoice.getPatient().getUuid() : null;
  }

  private InvoiceDTO mapToDTO(Invoice entity) {
    PatientDTO patientDTO = null;
    if (entity.getPatient() != null) {
      patientDTO = PatientDTO.builder()
          .uuid(entity.getPatient().getUuid())
          .firstName(entity.getPatient().getFirstName())
          .lastName(entity.getPatient().getLastName())
          .build();
    }

    List<InvoiceLineItemDTO> itemDTOs = entity.getItems() == null
        ? List.of()
        : entity.getItems().stream().map(this::mapItemToDTO).toList();

    return InvoiceDTO.builder()
        .id(entity.getId())
        .patient(patientDTO)
        .items(itemDTOs)
        .total(entity.getTotal())
        .balance(paymentService.getBalance(entity))
        .status(entity.getStatus())
        .issuedAt(entity.getIssuedAt())
        .voidedAt(entity.getVoidedAt())
        .voidReason(entity.getVoidReason())
        .voidedByUuid(entity.getVoidedByUuid())
        .payments(entity.getId() != null ? paymentService.getForInvoice(entity.getId()) : List.of())
        .build();
  }

  private InvoiceLineItemDTO mapItemToDTO(InvoiceLineItem item) {
    return InvoiceLineItemDTO.builder()
        .id(item.getId())
        .sourceType(item.getSourceType())
        .sourceId(item.getSourceId())
        .description(item.getDescription())
        .amount(item.getAmount())
        .insurerCoveredAmount(item.getInsurerCoveredAmount())
        .patientResponsibleAmount(item.getPatientResponsibleAmount())
        .insurerNameSnapshot(item.getInsurerNameSnapshot())
        .planNameSnapshot(item.getPlanNameSnapshot())
        .build();
  }
}
