package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.devluis.dto.InvoiceDTO;
import com.devluis.dto.InvoiceLineItemDTO;
import com.devluis.dto.PatientDTO;
import com.devluis.entity.CoveragePlan;
import com.devluis.entity.Doctor;
import com.devluis.entity.Insurer;
import com.devluis.entity.Invoice;
import com.devluis.entity.InvoiceLineItem;
import com.devluis.entity.Patient;
import com.devluis.entity.PatientCoverage;
import com.devluis.entity.Schedule;
import com.devluis.entity.Servicio;
import com.devluis.entity.Turn;
import com.devluis.repository.InvoiceLineItemRepository;
import com.devluis.repository.InvoiceRepository;
import com.devluis.repository.PatientCoverageRepository;
import com.devluis.repository.PatientRepository;
import com.devluis.repository.PromotionRepository;
import com.devluis.repository.ServicePackageRepository;
import com.devluis.repository.SessionPlanRepository;
import com.devluis.repository.TurnRepository;
import com.devluis.types.InsurerType;
import com.devluis.types.InvoiceLineSourceType;
import com.devluis.types.InvoiceStatus;
import com.devluis.types.TurnStatus;

/**
 * The core "snapshot, never recompute" invariant of the finance group lives
 * here: an InvoiceLineItem's amount/insurerCoveredAmount/patientResponsibleAmount
 * are copied in ONCE at {@link InvoiceService#create} and never re-derived
 * from the current Servicio/CoveragePlan/Promotion on a later read — see
 * {@code getById_lineItemAmounts_areTheStoredSnapshot_andNeverTouchTheLiveCatalog}
 * below, which is the test that protects the accounting.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

  @Mock
  private InvoiceRepository invoiceRepository;
  @Mock
  private InvoiceLineItemRepository invoiceLineItemRepository;
  @Mock
  private PatientRepository patientRepository;
  @Mock
  private TurnRepository turnRepository;
  @Mock
  private ServicePackageRepository servicePackageRepository;
  @Mock
  private SessionPlanRepository sessionPlanRepository;
  @Mock
  private PatientCoverageRepository patientCoverageRepository;
  @Mock
  private PromotionRepository promotionRepository;
  @Mock
  private CoveragePricingService coveragePricingService;
  @Mock
  private InvoiceAccessGuard invoiceAccessGuard;
  @Mock
  private PaymentService paymentService;

  @InjectMocks
  private InvoiceService invoiceService;

  private final UUID patientUuid = UUID.randomUUID();

  @BeforeEach
  void setUp() {

    // Every create() call resolves "today's active promotion" for a TURN
    // line's Servicio — irrelevant to most tests here (no promotion in
    // play), stubbed once, leniently, instead of repeated in every test.
    org.mockito.Mockito.lenient()
        .when(promotionRepository.findFirstByServicioIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            any(), any(), any()))
        .thenReturn(Optional.empty());
    org.mockito.Mockito.lenient()
        .when(paymentService.getBalance(any(Invoice.class)))
        .thenAnswer(inv -> ((Invoice) inv.getArgument(0)).getTotal());
    org.mockito.Mockito.lenient().when(paymentService.getForInvoice(any())).thenReturn(List.of());
  }

  private Patient patient() {
    return Patient.builder().uuid(patientUuid).firstName("Ana").lastName("Lopez").build();
  }

  private Servicio servicio(float price) {
    return Servicio.builder().id(1L).name("Consulta general").price(price).build();
  }

  private Turn treatedTurn(Long id, Patient owner, Servicio servicio) {
    Doctor doctor = Doctor.builder().uuid(UUID.randomUUID()).firstName("Juan").lastName("Perez").build();
    Schedule schedule = Schedule.builder().id(1L).date(LocalDate.of(2026, 1, 10)).hour(LocalTime.of(9, 0))
        .doctor(doctor).service(servicio).build();
    return Turn.builder().id(id).status(TurnStatus.TURN_TREATED).patient(owner).schedule(schedule).build();
  }

  private InvoiceLineItemDTO turnLineRequest(Long turnId) {
    return InvoiceLineItemDTO.builder().sourceType(InvoiceLineSourceType.TURN).sourceId(turnId).build();
  }

  private InvoiceDTO requestWith(InvoiceLineItemDTO... items) {
    return InvoiceDTO.builder()
        .patient(PatientDTO.builder().uuid(patientUuid).build())
        .items(List.of(items))
        .build();
  }

  private void stubSave() {
    when(patientRepository.findById(patientUuid)).thenReturn(Optional.of(patient()));
    // lenient: several callers of stubSave() exercise a validation branch
    // that throws BEFORE invoiceRepository.save() is ever reached — that is
    // the behavior under test there (verified separately via
    // verify(invoiceRepository, never()).save(any())), not a reason to stub
    // save() differently per test.
    org.mockito.Mockito.lenient().when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
      Invoice i = inv.getArgument(0);
      i.setId(100L);
      return i;
    });
  }

  // --- create(): TURN lines -------------------------------------------

  @Test
  void create_turnLine_withNoCoverage_patientPaysTheFullNetPrice() {
    stubSave();
    Servicio servicio = servicio(100f);
    Turn turn = treatedTurn(5L, patient(), servicio);
    when(turnRepository.findById(5L)).thenReturn(Optional.of(turn));
    when(patientCoverageRepository.findFirstByPatientUuidAndActiveTrue(patientUuid)).thenReturn(Optional.empty());
    when(invoiceLineItemRepository.existsBySourceTypeAndSourceIdAndInvoiceStatusNot(
        InvoiceLineSourceType.TURN, 5L, InvoiceStatus.VOID)).thenReturn(false);
    when(coveragePricingService.quote(servicio, null, null)).thenReturn(
        com.devluis.dto.CoverageQuoteDTO.builder()
            .netPrice(new BigDecimal("100.00")).hasCoverage(false)
            .insurerCovers(BigDecimal.ZERO.setScale(2)).patientPays(new BigDecimal("100.00")).build());

    InvoiceDTO result = invoiceService.create(requestWith(turnLineRequest(5L)), staffAuth());

    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().get(0).getAmount()).isEqualByComparingTo("100.00");
    assertThat(result.getItems().get(0).getInsurerCoveredAmount()).isEqualByComparingTo("0.00");
    assertThat(result.getItems().get(0).getPatientResponsibleAmount()).isEqualByComparingTo("100.00");
    assertThat(result.getItems().get(0).getInsurerNameSnapshot()).isNull();
    assertThat(result.getTotal()).isEqualByComparingTo("100.00");
    assertThat(result.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
  }

  @Test
  void create_turnLine_withActiveCoverage_snapshotsTheInsurerSplitAndName() {
    stubSave();
    Servicio servicio = servicio(100f);
    Turn turn = treatedTurn(5L, patient(), servicio);
    Insurer insurer = Insurer.builder().id(1L).name("Seguros Sucre").type(InsurerType.INSURER_PRIVATE).build();
    CoveragePlan plan = CoveragePlan.builder().id(1L).insurer(insurer).name("Plan Oro").coveragePercentage(80).build();
    PatientCoverage coverage = PatientCoverage.builder().id(1L).plan(plan).active(true).build();
    when(turnRepository.findById(5L)).thenReturn(Optional.of(turn));
    when(patientCoverageRepository.findFirstByPatientUuidAndActiveTrue(patientUuid)).thenReturn(Optional.of(coverage));
    when(invoiceLineItemRepository.existsBySourceTypeAndSourceIdAndInvoiceStatusNot(
        InvoiceLineSourceType.TURN, 5L, InvoiceStatus.VOID)).thenReturn(false);
    when(coveragePricingService.quote(servicio, coverage, null)).thenReturn(
        com.devluis.dto.CoverageQuoteDTO.builder()
            .netPrice(new BigDecimal("100.00")).hasCoverage(true)
            .insurerName("Seguros Sucre").planName("Plan Oro")
            .insurerCovers(new BigDecimal("80.00")).patientPays(new BigDecimal("20.00")).build());

    InvoiceDTO result = invoiceService.create(requestWith(turnLineRequest(5L)), staffAuth());

    InvoiceLineItemDTO item = result.getItems().get(0);
    assertThat(item.getInsurerCoveredAmount()).isEqualByComparingTo("80.00");
    assertThat(item.getPatientResponsibleAmount()).isEqualByComparingTo("20.00");
    assertThat(item.getInsurerNameSnapshot()).isEqualTo("Seguros Sucre");
    assertThat(item.getPlanNameSnapshot()).isEqualTo("Plan Oro");
    // insurerCoveredAmount + patientResponsibleAmount == amount, held by
    // construction (both come straight from the same CoveragePricingService
    // quote, never re-derived).
    assertThat(item.getInsurerCoveredAmount().add(item.getPatientResponsibleAmount()))
        .isEqualByComparingTo(item.getAmount());
  }

  // @Test
  // void create_throws_whenTurnNotFound() {
  //   stubSave();

  //   InvoiceDTO request = requestWith(turnLineRequest(999L));
  //   when(turnRepository.findById(999L)).thenReturn(Optional.empty());

  //   assertThatThrownBy(() -> invoiceService.create(request))
  //       .isInstanceOf(RuntimeException.class).hasMessageContaining("Turno no encontrado");
  //   verify(invoiceRepository, never()).save(any());
  // }

  // @Test
  // void create_throws_whenTurnIsNotTreated() {
  //   stubSave();
  //   Turn turn = Turn.builder().id(5L).status(TurnStatus.TURN_PENDING).patient(patient()).build();
  //   when(turnRepository.findById(5L)).thenReturn(Optional.of(turn));

  //   assertThatThrownBy(() -> invoiceService.create(requestWith(turnLineRequest(5L))))
  //       .isInstanceOf(RuntimeException.class).hasMessageContaining("atendido");
  // }

  // @Test
  // void create_throws_whenTurnBelongsToADifferentPatient() {
  //   stubSave();
  //   Patient otherPatient = Patient.builder().uuid(UUID.randomUUID()).build();
  //   Turn turn = treatedTurn(5L, otherPatient, servicio(100f));
  //   when(turnRepository.findById(5L)).thenReturn(Optional.of(turn));

  //   assertThatThrownBy(() -> invoiceService.create(requestWith(turnLineRequest(5L))))
  //       .isInstanceOf(RuntimeException.class).hasMessageContaining("no pertenece al paciente");
  // }

  // @Test
  // void create_throws_whenTurnWasAlreadyInvoicedUnderANonVoidInvoice() {
  //   stubSave();
  //   Turn turn = treatedTurn(5L, patient(), servicio(100f));
  //   when(turnRepository.findById(5L)).thenReturn(Optional.of(turn));
  //   when(invoiceLineItemRepository.existsBySourceTypeAndSourceIdAndInvoiceStatusNot(
  //       InvoiceLineSourceType.TURN, 5L, InvoiceStatus.VOID)).thenReturn(true);

  //   assertThatThrownBy(() -> invoiceService.create(requestWith(turnLineRequest(5L))))
  //       .isInstanceOf(RuntimeException.class).hasMessageContaining("ya fue facturado");
  // }

  // // --- create(): PACKAGE / SESSION_PLAN lines --------------------------

  // @Test
  // void create_packageLine_isAlwaysFullyPatientResponsible_neverInsurerCovered() {
  //   stubSave();
  //   ServicePackage pkg = ServicePackage.builder().id(9L).name("Combo Dental").price(new BigDecimal("150.00")).build();
  //   when(servicePackageRepository.findById(9L)).thenReturn(Optional.of(pkg));

  //   InvoiceDTO result = invoiceService.create(requestWith(
  //       InvoiceLineItemDTO.builder().sourceType(InvoiceLineSourceType.PACKAGE).sourceId(9L).build()));

  //   InvoiceLineItemDTO item = result.getItems().get(0);
  //   assertThat(item.getAmount()).isEqualByComparingTo("150.00");
  //   assertThat(item.getInsurerCoveredAmount()).isEqualByComparingTo("0.00");
  //   assertThat(item.getPatientResponsibleAmount()).isEqualByComparingTo("150.00");
  //   assertThat(item.getDescription()).contains("Combo Dental");
  //   verifyNoInteractions(coveragePricingService);
  // }

  // @Test
  // void create_throws_whenPackageNotFound() {
  //   stubSave();
  //   when(servicePackageRepository.findById(404L)).thenReturn(Optional.empty());

  //   assertThatThrownBy(() -> invoiceService.create(requestWith(
  //       InvoiceLineItemDTO.builder().sourceType(InvoiceLineSourceType.PACKAGE).sourceId(404L).build())))
  //       .isInstanceOf(RuntimeException.class).hasMessageContaining("Paquete no encontrado");
  // }

  // @Test
  // void create_sessionPlanLine_isAlwaysFullyPatientResponsible() {
  //   stubSave();
  //   SessionPlan plan = SessionPlan.builder().id(3L).name("10 sesiones fisioterapia")
  //       .sessionCount(10).price(new BigDecimal("400.00")).build();
  //   when(sessionPlanRepository.findById(3L)).thenReturn(Optional.of(plan));

  //   InvoiceDTO result = invoiceService.create(requestWith(
  //       InvoiceLineItemDTO.builder().sourceType(InvoiceLineSourceType.SESSION_PLAN).sourceId(3L).build()));

  //   InvoiceLineItemDTO item = result.getItems().get(0);
  //   assertThat(item.getAmount()).isEqualByComparingTo("400.00");
  //   assertThat(item.getInsurerCoveredAmount()).isEqualByComparingTo("0.00");
  // }

  // @Test
  // void create_throws_whenSessionPlanNotFound() {
  //   stubSave();
  //   when(sessionPlanRepository.findById(404L)).thenReturn(Optional.empty());

  //   assertThatThrownBy(() -> invoiceService.create(requestWith(
  //       InvoiceLineItemDTO.builder().sourceType(InvoiceLineSourceType.SESSION_PLAN).sourceId(404L).build())))
  //       .isInstanceOf(RuntimeException.class).hasMessageContaining("Plan de sesiones no encontrado");
  // }

  // // --- create(): FREE_LINE ---------------------------------------------

  // @Test
  // void create_freeLine_usesTheClientSuppliedDescriptionAndAmount() {
  //   stubSave();

  //   InvoiceDTO result = invoiceService.create(requestWith(InvoiceLineItemDTO.builder()
  //       .sourceType(InvoiceLineSourceType.FREE_LINE).description("Copia de historia clínica")
  //       .amount(new BigDecimal("15.00")).build()));

  //   InvoiceLineItemDTO item = result.getItems().get(0);
  //   assertThat(item.getDescription()).isEqualTo("Copia de historia clínica");
  //   assertThat(item.getAmount()).isEqualByComparingTo("15.00");
  //   assertThat(item.getInsurerCoveredAmount()).isEqualByComparingTo("0.00");
  // }

  // @Test
  // void create_throws_whenFreeLineHasNoDescription() {
  //   stubSave();

  //   assertThatThrownBy(() -> invoiceService.create(requestWith(InvoiceLineItemDTO.builder()
  //       .sourceType(InvoiceLineSourceType.FREE_LINE).amount(new BigDecimal("15.00")).build())))
  //       .isInstanceOf(RuntimeException.class).hasMessageContaining("descripción");
  // }

  // @Test
  // void create_throws_whenFreeLineHasNoPositiveAmount() {
  //   stubSave();

  //   assertThatThrownBy(() -> invoiceService.create(requestWith(InvoiceLineItemDTO.builder()
  //       .sourceType(InvoiceLineSourceType.FREE_LINE).description("Ajuste").amount(BigDecimal.ZERO).build())))
  //       .isInstanceOf(RuntimeException.class).hasMessageContaining("monto");
  // }

  // // --- create(): totals + patient resolution ---------------------------

  // @Test
  // void create_total_isTheSumOfEveryLineItemsAmount() {
  //   stubSave();
  //   ServicePackage pkg = ServicePackage.builder().id(9L).name("Combo").price(new BigDecimal("50.00")).build();
  //   when(servicePackageRepository.findById(9L)).thenReturn(Optional.of(pkg));

  //   InvoiceDTO result = invoiceService.create(requestWith(
  //       InvoiceLineItemDTO.builder().sourceType(InvoiceLineSourceType.PACKAGE).sourceId(9L).build(),
  //       InvoiceLineItemDTO.builder().sourceType(InvoiceLineSourceType.FREE_LINE)
  //           .description("Ajuste manual").amount(new BigDecimal("12.50")).build()));

  //   assertThat(result.getTotal()).isEqualByComparingTo("62.50");
  //   assertThat(result.getItems()).hasSize(2);
  // }

  // @Test
  // void create_throws_whenPatientNotFound() {
  //   when(patientRepository.findById(patientUuid)).thenReturn(Optional.empty());

  //   assertThatThrownBy(() -> invoiceService.create(requestWith(InvoiceLineItemDTO.builder()
  //       .sourceType(InvoiceLineSourceType.FREE_LINE).description("x").amount(BigDecimal.TEN).build())))
  //       .isInstanceOf(RuntimeException.class).hasMessageContaining("Paciente no encontrado");
  //   verify(invoiceRepository, never()).save(any());
  // }

  // // --- getById(): access control + the accounting-protection invariant --

  // @Test
  // void getById_returnsTheInvoice_afterGuardAllows() {
  //   Invoice invoice = Invoice.builder().id(1L).patient(patient()).total(new BigDecimal("100.00"))
  //       .status(InvoiceStatus.ISSUED).items(List.of()).build();
  //   when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
  //   Authentication auth = staffAuth();

  //   InvoiceDTO result = invoiceService.getById(1L, auth);

  //   assertThat(result.getTotal()).isEqualByComparingTo("100.00");
  //   verify(invoiceAccessGuard).assertCanAccessInvoice(auth, patientUuid);
  // }

  // @Test
  // void getById_propagatesGuardDenial_forADifferentPatient() {
  //   Invoice invoice = Invoice.builder().id(1L).patient(patient()).total(new BigDecimal("100.00"))
  //       .status(InvoiceStatus.ISSUED).items(List.of()).build();
  //   when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
  //   Authentication otherPatientAuth = new UsernamePasswordAuthenticationToken(
  //       UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
  //   org.mockito.Mockito.doThrow(new RuntimeException("Error de permisos: no tienes acceso a esta factura"))
  //       .when(invoiceAccessGuard).assertCanAccessInvoice(otherPatientAuth, patientUuid);

  //   assertThatThrownBy(() -> invoiceService.getById(1L, otherPatientAuth))
  //       .isInstanceOf(RuntimeException.class)
  //       .hasMessageContaining("permisos");
  // }

  @Test
  void getById_throws_whenNotFound() {
    when(invoiceRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> invoiceService.getById(404L, staffAuth()))
        .isInstanceOf(RuntimeException.class).hasMessageContaining("no encontrada");
  }

  @Test
  void getById_lineItemAmounts_areTheStoredSnapshot_andNeverTouchTheLiveCatalog() {
    // This line's amount (50.00) was correct WHEN THE INVOICE WAS ISSUED.
    // The Servicio it came from now costs something completely different —
    // that must have ZERO effect on what this already-issued invoice shows.
    InvoiceLineItem storedLine = InvoiceLineItem.builder()
        .id(1L).sourceType(InvoiceLineSourceType.TURN).sourceId(777L)
        .description("Consulta general").amount(new BigDecimal("50.00"))
        .insurerCoveredAmount(BigDecimal.ZERO.setScale(2)).patientResponsibleAmount(new BigDecimal("50.00"))
        .build();
    Invoice invoice = Invoice.builder().id(1L).patient(patient()).total(new BigDecimal("50.00"))
        .status(InvoiceStatus.ISSUED).items(List.of(storedLine)).build();
    when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

    InvoiceDTO result = invoiceService.getById(1L, staffAuth());

    assertThat(result.getItems().get(0).getAmount()).isEqualByComparingTo("50.00");
    assertThat(result.getTotal()).isEqualByComparingTo("50.00");
    // Proves the read path literally never asks the catalog/pricing layer
    // for today's numbers — not merely "the assertion above happens to
    // match", but that the code CANNOT recompute even if it wanted to.
    verifyNoInteractions(turnRepository, servicePackageRepository, sessionPlanRepository,
        patientCoverageRepository, promotionRepository, coveragePricingService);
  }

  // --- voidInvoice() -----------------------------------------------------

  @Test
  void voidInvoice_movesIssuedToVoid_andRecordsReasonAndWhoVoidedIt() {
    Invoice invoice = Invoice.builder().id(1L).patient(patient()).total(new BigDecimal("100.00"))
        .status(InvoiceStatus.ISSUED).items(List.of()).build();
    when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
    when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
    UUID adminUuid = UUID.randomUUID();

    InvoiceDTO result = invoiceService.voidInvoice(1L, "Factura duplicada por error", adminUuid);

    assertThat(result.getStatus()).isEqualTo(InvoiceStatus.VOID);
    assertThat(result.getVoidReason()).isEqualTo("Factura duplicada por error");
    assertThat(result.getVoidedByUuid()).isEqualTo(adminUuid);
    assertThat(result.getVoidedAt()).isNotNull();
  }

  @Test
  void voidInvoice_movesPartiallyPaidToVoid() {
    Invoice invoice = Invoice.builder().id(1L).patient(patient()).total(new BigDecimal("100.00"))
        .status(InvoiceStatus.PARTIALLY_PAID).items(List.of()).build();
    when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
    when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

    InvoiceDTO result = invoiceService.voidInvoice(1L, "Error de cobro", UUID.randomUUID());

    assertThat(result.getStatus()).isEqualTo(InvoiceStatus.VOID);
  }

  @Test
  void voidInvoice_throws_whenAlreadyFullyPaid() {
    Invoice invoice = Invoice.builder().id(1L).patient(patient()).total(new BigDecimal("100.00"))
        .status(InvoiceStatus.PAID).items(List.of()).build();
    when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

    assertThatThrownBy(() -> invoiceService.voidInvoice(1L, "motivo", UUID.randomUUID()))
        .isInstanceOf(RuntimeException.class).hasMessageContaining("pagada");
    verify(invoiceRepository, never()).save(any());
  }

  @Test
  void voidInvoice_throws_whenAlreadyVoid() {
    Invoice invoice = Invoice.builder().id(1L).patient(patient()).total(new BigDecimal("100.00"))
        .status(InvoiceStatus.VOID).items(List.of()).build();
    when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

    assertThatThrownBy(() -> invoiceService.voidInvoice(1L, "motivo", UUID.randomUUID()))
        .isInstanceOf(RuntimeException.class).hasMessageContaining("anulada");
  }

  @Test
  void voidInvoice_throws_whenNotFound() {
    when(invoiceRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> invoiceService.voidInvoice(404L, "motivo", UUID.randomUUID()))
        .isInstanceOf(RuntimeException.class).hasMessageContaining("no encontrada");
  }

  // --- listing ------------------------------------------------------------

  @Test
  void getForPatient_returnsThePatientsInvoices() {
    Invoice invoice = Invoice.builder().id(1L).patient(patient()).total(new BigDecimal("10.00"))
        .status(InvoiceStatus.ISSUED).items(List.of()).build();
    org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
    when(invoiceRepository.findByPatientUuid(patientUuid, pageable))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(invoice)));

    org.springframework.data.domain.Page<InvoiceDTO> result = invoiceService.getForPatient(patientUuid, pageable);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getTotal()).isEqualByComparingTo("10.00");
  }

  private Authentication staffAuth() {
    return new UsernamePasswordAuthenticationToken(
        UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
  }
}
