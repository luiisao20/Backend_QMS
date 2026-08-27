package com.devluis.services;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.devluis.dto.ClinicalSummaryDTO;
import com.devluis.dto.EncounterDTO;
import com.devluis.dto.PrescriptionDTO;
import com.devluis.dto.PrescriptionItemDTO;
import com.devluis.entity.Patient;
import com.devluis.repository.PatientRepository;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * Genera el resumen de historia clinica que el medico ve en la ficha del
 * paciente, durante la consulta.
 *
 * NO reimplementa autorizacion ni auditoria. Llama a
 * {@link EncounterService#getHistoryForPatient} y
 * {@link PrescriptionService#getHistoryForPatient}, que ya hacen las tres
 * cosas en el orden correcto: {@link ClinicalAccessGuard#resolveDoctorFilter}
 * (rechaza a quien no sea DOCTOR/ADMIN y acota al doctor a SUS propios
 * encuentros con ese paciente), la consulta, y el asiento en
 * {@link ClinicalAccessLogService}. Duplicar esa logica aqui seria abrir una
 * segunda puerta a datos clinicos con sus propias reglas, que es exactamente
 * como se filtran las historias clinicas.
 *
 * Consecuencia deliberada: un doctor que no trato a este paciente recibe un
 * resumen vacio, no el historial de otro colega. Y el resumen que ve un
 * ROLE_DOCTOR puede ser mas corto que el de un ROLE_ADMIN — es el mismo
 * alcance que ya tiene la pantalla de historial clinico.
 *
 * DE-IDENTIFICACION: lo unico que sale de este servidor son hechos clinicos
 * anonimos. Fuera nombre, cedula, direccion, telefono, email, uuid del
 * paciente y nombre del medico tratante. Queda edad, sexo y el contenido
 * clinico. El medico ya sabe a quien esta atendiendo: lo tiene enfrente. El
 * modelo no necesita saberlo para resumir.
 *
 * RIESGO RESIDUAL ASUMIDO: {@code clinicalNotes} es texto libre. Si un medico
 * escribio ahi el nombre del paciente, ese nombre viaja. Se incluye igual
 * porque es el campo clinicamente mas valioso y sin el el resumen pierde casi
 * todo su sentido. Si la clinica decide que no es aceptable, se quita la
 * linea marcada mas abajo y el resto sigue funcionando.
 */
@Service
@RequiredArgsConstructor
public class ClinicalSummaryService {

  private static final String WEBHOOK = "clinical-summary";

  private final EncounterService encounterService;
  private final PrescriptionService prescriptionService;
  private final PatientRepository patientRepository;
  private final ClinicalAccessGuard clinicalAccessGuard;
  private final N8nClient n8nClient;

  /**
   * Tope de encuentros que entran al resumen. No es una restriccion de
   * privacidad sino de contexto y latencia: un historial de 80 encuentros
   * tarda mucho mas y el modelo pierde precision. Lo que queda afuera se
   * informa en la respuesta, nunca en silencio.
   */
  @Value("${ai.summary.max-encounters:20}")
  private int maxEncounters;

  public ClinicalSummaryDTO generate(UUID patientUuid, Authentication auth) {
    // Explicito y primero, aunque los servicios de abajo lo repitan: deja
    // claro en este archivo que nada se lee antes de autorizar. Es una
    // comprobacion pura, llamarla dos veces no tiene costo.
    clinicalAccessGuard.resolveDoctorFilter(auth);

    Patient patient = patientRepository.findById(patientUuid)
        .orElseThrow(() -> new RuntimeException("Paciente no encontrado"));

    // Los mas recientes primero para el tope; despues se invierte, porque un
    // resumen se lee mejor en orden cronologico.
    Page<EncounterDTO> encounterPage = encounterService.getHistoryForPatient(
        patientUuid, auth, PageRequest.of(0, maxEncounters, Sort.by(Sort.Direction.DESC, "createdAt")));

    // Una consulta puede dejar varias recetas, asi que se pide un margen
    // sobre el tope de encuentros en vez de asumir 1:1.
    Page<PrescriptionDTO> prescriptionPage = prescriptionService.getHistoryForPatient(
        patientUuid, auth, PageRequest.of(0, maxEncounters * 3, Sort.by(Sort.Direction.DESC, "createdAt")));

    List<EncounterDTO> encounters = new ArrayList<>(encounterPage.getContent());
    encounters.sort(Comparator.comparing(EncounterDTO::getCreatedAt,
        Comparator.nullsLast(Comparator.naturalOrder())));

    Map<Long, List<PrescriptionDTO>> byEncounter = prescriptionPage.getContent().stream()
        .filter(p -> p.getEncounterId() != null)
        .collect(Collectors.groupingBy(PrescriptionDTO::getEncounterId));

    DeidentifiedHistory payload = DeidentifiedHistory.builder()
        .edad(calcularEdad(patient.getBirthday()))
        .sexo(traducirSexo(patient))
        .encuentros(encounters.stream()
            .map(e -> toDeidentified(e, byEncounter.get(e.getId())))
            .toList())
        .build();

    // El asiento en ClinicalAccessLog ya quedo escrito por los dos servicios
    // de arriba, ANTES de esta llamada. Es lo correcto: el dato clinico se
    // leyo de la base en ese momento, aunque n8n falle despues.
    SummaryResponse respuesta = n8nClient.post(WEBHOOK, payload, SummaryResponse.class);

    return ClinicalSummaryDTO.builder()
        .resumen(respuesta == null || respuesta.getResumen() == null
            ? "El asistente no devolvio un resumen. Revise el historial completo."
            : respuesta.getResumen())
        .encuentros(encounters)
        .recetas(prescriptionPage.getContent())
        .totalEncuentros(encounterPage.getTotalElements())
        .encuentrosResumidos(encounters.size())
        .build();
  }

  private int calcularEdad(LocalDate birthday) {
    // birthday es nullable=false en Patient, pero un NPE aqui dejaria al
    // medico sin resumen por un dato demografico que no cambia nada clinico.
    if (birthday == null) {
      return 0;
    }
    return Period.between(birthday, LocalDate.now()).getYears();
  }

  private String traducirSexo(Patient patient) {
    if (patient.getGender() == null) {
      return "sin registro";
    }
    return switch (patient.getGender()) {
      case GENDER_MALE -> "M";
      case GENDER_FEMALE -> "F";
      case GENDER_OTHER -> "otro";
    };
  }

  private DeidentifiedEncounter toDeidentified(EncounterDTO e, List<PrescriptionDTO> recetas) {
    return DeidentifiedEncounter.builder()
        .fecha(e.getVisitDate() != null
            ? e.getVisitDate().toString()
            : (e.getCreatedAt() != null ? e.getCreatedAt().toLocalDate().toString() : null))
        .motivo(e.getReasonForVisit())
        .dx(e.getDiagnosis())
        // <-- Texto libre. Ver "RIESGO RESIDUAL ASUMIDO" en el docblock de la
        //     clase. Borrar esta linea si la clinica decide no enviarlo.
        .notas(e.getClinicalNotes())
        .rx(formatearRecetas(recetas))
        .build();
  }

  /**
   * Aplana las recetas de un encuentro a una linea de texto. El modelo no
   * necesita la estructura, necesita leer "que se le indico"; y un JSON
   * anidado por item gasta contexto sin aportar nada al resumen.
   */
  private String formatearRecetas(List<PrescriptionDTO> recetas) {
    if (recetas == null || recetas.isEmpty()) {
      return null;
    }
    return recetas.stream()
        .filter(r -> r.getItems() != null)
        .flatMap(r -> r.getItems().stream())
        .map(this::formatearItem)
        .filter(s -> !s.isBlank())
        .collect(Collectors.joining("; "));
  }

  private String formatearItem(PrescriptionItemDTO item) {
    List<String> partes = new ArrayList<>();
    if (item.getMedication() != null && !item.getMedication().isBlank()) {
      partes.add(item.getMedication());
    }
    if (item.getDosage() != null && !item.getDosage().isBlank()) {
      partes.add(item.getDosage());
    }
    if (item.getFrequency() != null && !item.getFrequency().isBlank()) {
      partes.add(item.getFrequency());
    }
    if (item.getDuration() != null && !item.getDuration().isBlank()) {
      partes.add(item.getDuration());
    }
    return String.join(" ", partes);
  }

  // --------------------------------------------------------------------------
  // Formas de cable. Publicas de paquete a proposito: son el contrato con el
  // workflow "clinical-summary" de n8n, no tipos de dominio. El nodo If de ese
  // workflow evalua {{ $json.body.encuentros.length }}, asi que el nombre del
  // campo "encuentros" es parte del contrato: cambiarlo rompe el flujo.
  // --------------------------------------------------------------------------

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  static class DeidentifiedHistory {
    private int edad;
    private String sexo;
    private List<DeidentifiedEncounter> encuentros;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  static class DeidentifiedEncounter {
    private String fecha;
    private String motivo;
    private String dx;
    private String rx;
    private String notas;
  }

  @Data
  @NoArgsConstructor
  static class SummaryResponse {
    private String resumen;
  }
}
