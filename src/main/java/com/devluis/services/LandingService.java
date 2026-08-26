package com.devluis.services;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.devluis.entity.Branding;
import com.devluis.entity.Doctor;
import com.devluis.entity.Insurer;
import com.devluis.entity.Schedule;
import com.devluis.entity.Servicio;
import com.devluis.entity.Stablishment;
import com.devluis.repository.BrandingRepository;
import com.devluis.repository.DoctorRepository;
import com.devluis.repository.InsurerRepository;
import com.devluis.repository.PatientRepository;
import com.devluis.repository.ScheduleRepository;
import com.devluis.repository.ServiceRepository;
import com.devluis.repository.StablishmentRepository;
import com.devluis.types.ScheduleStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.Data;

/**
 * Datos de la landing publica.
 *
 * DISENO. Cada seccion arranca desde su archivo de contrato en
 * {@code resources/landing/*.json} y encima se SUPERPONEN los datos vivos. No
 * se rearman 13 arboles de DTOs a mano por dos razones:
 *
 * 1. La forma queda garantizada, porque sale del propio contrato. Un DTO escrito
 *    aparte se desincroniza en silencio: el cliente castea el cuerpo sin
 *    validarlo, asi que un campo renombrado no da error, llega undefined y la
 *    seccion pinta un hueco.
 * 2. Cada seccion es MIXTA. "Especialidades" tiene un titulo de marketing y una
 *    lista de servicios reales; "Medicos" tiene un encabezado fijo y fichas de
 *    la base. Separar editorial de dato vivo campo por campo es exactamente lo
 *    que hace este servicio.
 *
 * Las cinco secciones puramente editoriales (quick-access, how-it-works,
 * reviews, faq, public-insurance) no tienen fuente en la base y se devuelven tal
 * cual. Se sirven desde aca igual para que la landing tenga UNA sola URL base y
 * no dos origenes conviviendo.
 */
@Service
@Data
public class LandingService {

  /** Secciones validas. Cualquier otra es 404, no un archivo arbitrario del classpath. */
  private static final Set<String> SECCIONES = Set.of(
      "site", "hero", "quick-access", "stats", "insurers", "specialties",
      "how-it-works", "doctors", "locations", "reviews", "faq",
      "public-insurance", "coverage");

  /** Medicos que entran en la grilla; el resto se resume en header.totalCount. */
  private static final int DOCTORES_EN_PORTADA = 4;

  /** Proximos cupos libres que se muestran por medico. */
  private static final int CUPOS_POR_DOCTOR = 2;

  /**
   * Instancia propia, NO inyectada.
   *
   * Spring Boot 4 cambio la autoconfiguracion de Jackson y ya no expone un bean
   * de ObjectMapper: inyectarlo hacia fallar el arranque entero con
   * UnsatisfiedDependencyException, no solo esta clase.
   *
   * Tampoco hace falta que sea el del contexto. Aca solo se lee un archivo del
   * classpath y se arman nodos; la RESPUESTA la serializa el convertidor de
   * Spring, no este mapper, asi que ninguna configuracion de serializacion de
   * la app se pierde por usar uno aparte.
   *
   * Con inicializador, Lombok lo deja fuera del constructor de campos finales.
   */
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final BrandingRepository brandingRepository;
  private final StablishmentRepository stablishmentRepository;
  private final ServiceRepository serviceRepository;
  private final DoctorRepository doctorRepository;
  private final InsurerRepository insurerRepository;
  private final ScheduleRepository scheduleRepository;
  private final PatientRepository patientRepository;

  /** El contrato leido del jar. Se cachea: el archivo no cambia en caliente. */
  private final Map<String, JsonNode> plantillas = new ConcurrentHashMap<>();

  /**
   * Acepta "site" y tambien "site.json".
   *
   * El cliente pide /api/landing/site.json porque estas secciones nacieron como
   * archivos estaticos y su token de URL base prometia que apuntarlo a la API
   * era el UNICO cambio necesario. Absorber el sufijo aca honra esa promesa y
   * evita tocar trece URLs del front para una diferencia cosmetica.
   */
  private String normalizar(String seccion) {
    if (seccion == null) {
      return "";
    }
    return seccion.endsWith(".json")
        ? seccion.substring(0, seccion.length() - 5)
        : seccion;
  }

  public boolean existe(String seccion) {
    return SECCIONES.contains(normalizar(seccion));
  }

  /**
   * Devuelve el JSON YA SERIALIZADO, no un JsonNode.
   *
   * Spring Boot 4 no serializa JsonNode como JSON literal: lo trata como un
   * POJO y publica sus getters, asi que el cuerpo salia
   * {"array":false,"nodeType":"OBJECT",...} con un 200 impecable. Un fallo
   * mudo: el codigo de estado no delata nada y el cliente castea sin validar.
   *
   * Serializando aca con el mapper propio, el resultado no depende de que
   * convertidor elija el framework para un tipo de Jackson.
   */
  public String get(String seccionCruda) {
    String seccion = normalizar(seccionCruda);
    ObjectNode raiz = plantilla(seccion).deepCopy();

    switch (seccion) {
      case "site" -> superponerSite(raiz);
      case "hero" -> superponerHero(raiz);
      case "stats" -> superponerStats(raiz);
      case "insurers" -> superponerInsurers(raiz);
      case "specialties" -> superponerSpecialties(raiz);
      case "doctors" -> superponerDoctors(raiz);
      case "locations" -> superponerLocations(raiz);
      case "coverage" -> superponerCoverage(raiz);
      // quick-access, how-it-works, reviews, faq, public-insurance: editorial
      // puro, sin fuente en la base. Se devuelven tal cual.
      default -> {
      }
    }

    // El $comment documenta el contrato para quien lee el archivo; no tiene por
    // que viajar por la red en cada carga de la landing.
    raiz.remove("$comment");

    try {
      return objectMapper.writeValueAsString(raiz);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new RuntimeException("No se pudo serializar la seccion '" + seccion + "'");
    }
  }

  // ---------------------------------------------------------------- overlays

  private void superponerSite(ObjectNode raiz) {
    Branding b = branding();
    if (b == null) {
      return;
    }
    objeto(raiz, "brand").put("name", b.getName());

    ObjectNode contacto = objeto(raiz, "contact");
    ponerSiHay(contacto, "phone", b.getPhone());
    ponerSiHay(contacto, "emergencyPhone", b.getEmergencyPhone());
    ponerSiHay(contacto, "whatsapp", b.getWhatsapp());
    ponerSiHay(contacto, "email", b.getEmail());
  }

  /**
   * Solo {@code availability} es vivo: el titular, el lead y las pildoras son
   * editoriales y se dejan como estan.
   *
   * El agregado es "cupos libres de hoy por servicio", que es lo que la portada
   * promete. No se deriva de la grilla de reserva de un medico.
   */
  private void superponerHero(ObjectNode raiz) {
    List<Schedule> libresHoy = scheduleRepository.findAll().stream()
        .filter(s -> LocalDate.now().equals(s.getDate()))
        .filter(s -> s.getStatus() == ScheduleStatus.STATUS_FREE)
        .toList();

    Map<String, Integer> porServicio = new LinkedHashMap<>();
    for (Schedule s : libresHoy) {
      if (s.getService() == null || s.getStablishment() == null) {
        continue;
      }
      String clave = s.getService().getName() + "|" + s.getStablishment().getName();
      porServicio.merge(clave, 1, Integer::sum);
    }

    // availability es UN OBJETO, no un arreglo: el contrato lo define como
    // { label, count, unit, specialty, location } y el organismo lee esos
    // campos por nombre. Devolver un arreglo da 200 con JSON valido y el card
    // sale VACIO, porque .label sobre un arreglo es undefined.
    //
    // Se muestra el par (servicio, sede) con MAS cupos libres hoy: es la
    // promesa de la portada, "hay agenda abierta ahora", no un listado.
    var mejor = porServicio.entrySet().stream()
        .max((a, x) -> a.getValue() - x.getValue())
        .orElse(null);

    if (mejor != null) {
      String[] partes = mejor.getKey().split("|", 2);
      ObjectNode disponibilidad = objeto(raiz, "availability");
      // label y unit son editoriales: se conservan tal como vienen del contrato.
      disponibilidad.put("count", mejor.getValue());
      disponibilidad.put("specialty", partes[0]);
      disponibilidad.put("location", partes.length > 1 ? partes[1] : "");
    }
  }

  /**
   * value pasa a ser un conteo real. percent NO se toca: el README del contrato
   * avisa que en el item con sufijo "%" las dos cifras son la misma magnitud y
   * tienen que coincidir, mientras que en los otros el anillo es decoracion.
   * Pisar percent con un numero real romperia ese invariante visual.
   */
  private void superponerStats(ObjectNode raiz) {
    Map<String, Long> reales = Map.of(
        "especialistas", doctorRepository.count(),
        "pacientes", patientRepository.count(),
        "sedes", stablishmentRepository.count(),
        "especialidades", serviceRepository.count());

    for (JsonNode item : raiz.withArray("items")) {
      Long valor = reales.get(item.path("id").asText());
      if (valor != null && valor > 0) {
        ((ObjectNode) item).put("value", valor);
      }
    }
  }

  private void superponerInsurers(ObjectNode raiz) {
    List<Insurer> aseguradoras = insurerRepository.findAll();
    if (aseguradoras.isEmpty()) {
      return;
    }
    ArrayNode items = objectMapper.createArrayNode();
    for (Insurer i : aseguradoras) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("id", "aseguradora-" + i.getId());
      n.put("name", i.getName());
      items.add(n);
    }
    raiz.set("items", items);
  }

  /**
   * Los servicios reales entran en el PRIMER grupo del contrato. La agrupacion
   * ("Consulta", "Procedimientos"...) es una decision editorial que la entidad
   * Servicio no modela: no tiene categoria. Inventar grupos aca seria adivinar.
   */
  private void superponerSpecialties(ObjectNode raiz) {
    List<Servicio> servicios = serviceRepository.findAll();
    // Servicio no modela la relacion inversa con Doctor: el @ManyToMany vive
    // solo del lado de Doctor.services. Se cuenta recorriendo los medicos una
    // vez, en lugar de una consulta por servicio.
    List<Doctor> medicos = doctorRepository.findAll();
    if (servicios.isEmpty()) {
      return;
    }

    ArrayNode grupos = raiz.withArray("groups");
    if (grupos.isEmpty()) {
      return;
    }
    ObjectNode primero = (ObjectNode) grupos.get(0);

    // Fotos y descripciones del contrato, aplanadas de todos los grupos.
    List<JsonNode> plantillaSpecialties = new ArrayList<>();
    for (JsonNode g : grupos) {
      g.withArray("specialties").forEach(plantillaSpecialties::add);
    }
    if (plantillaSpecialties.isEmpty()) {
      return;
    }
    int indice = 0;

    ArrayNode lista = objectMapper.createArrayNode();
    for (Servicio s : servicios) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("id", "servicio-" + s.getId());
      n.put("name", s.getName());
      // description e image NO se vacian: Servicio no los modela, y ponerlos
      // en "" deja un <img src=""> que el navegador pinta como imagen rota.
      // Se reutilizan ciclicamente las del contrato, que son fotos reales de
      // la clinica. Un campo sin fuente conserva el valor del contrato.
      JsonNode modelo = plantillaSpecialties.get(indice % plantillaSpecialties.size());
      indice++;
      n.put("description", modelo.path("description").asText(""));
      n.put("image", modelo.path("image").asText(""));
      long conEsteServicio = medicos.stream()
          .filter(d -> d.getServices() != null
              && d.getServices().stream().anyMatch(x -> x.getId().equals(s.getId())))
          .count();
      n.put("doctorCount", conEsteServicio);
      ObjectNode precio = objectMapper.createObjectNode();
      precio.put("amount", s.getPrice() == null ? 0 : s.getPrice());
      precio.put("currency", "USD");
      n.set("priceFrom", precio);
      n.set("tags", objectMapper.createArrayNode());
      lista.add(n);
    }
    primero.set("specialties", lista);
  }

  private void superponerDoctors(ObjectNode raiz) {
    List<Doctor> medicos = doctorRepository.findAll();
    if (medicos.isEmpty()) {
      return;
    }

    objeto(raiz, "header").put("totalCount", medicos.size());

    // Mismas fotos de referencia que trae el contrato. Doctor no guarda foto,
    // y vaciar el campo dejaba cuatro imagenes rotas con su alt a la vista.
    List<String> fotos = imagenesDe(raiz.withArray("items"));
    int i = 0;

    ArrayNode items = objectMapper.createArrayNode();
    for (Doctor d : medicos.stream().limit(DOCTORES_EN_PORTADA).toList()) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("id", "doctor-" + d.getUuid());
      n.put("name", "Dr(a). " + nvl(d.getFirstName()) + " " + nvl(d.getLastName()));
      n.put("specialty", nvl(d.getSpeciality()));
      n.put("registrationNumber", nvl(d.getCi()));
      n.put("image", fotos.isEmpty() ? "" : fotos.get(i % fotos.size()));
      i++;
      n.set("upcomingSlots", proximosCupos(d));
      items.add(n);
    }
    raiz.set("items", items);
  }

  /** Cupos libres de hoy en adelante. Es el unico dato vivo de esta seccion. */
  private ArrayNode proximosCupos(Doctor d) {
    ArrayNode slots = objectMapper.createArrayNode();
    LocalDate hoy = LocalDate.now();

    scheduleRepository.findAll().stream()
        .filter(s -> s.getDoctor() != null && s.getDoctor().getUuid().equals(d.getUuid()))
        .filter(s -> s.getStatus() == ScheduleStatus.STATUS_FREE)
        .filter(s -> s.getDate() != null && !s.getDate().isBefore(hoy))
        .sorted((a, b) -> {
          int porFecha = a.getDate().compareTo(b.getDate());
          return porFecha != 0 ? porFecha : a.getHour().compareTo(b.getHour());
        })
        .limit(CUPOS_POR_DOCTOR)
        .forEach(s -> {
          ObjectNode n = objectMapper.createObjectNode();
          n.put("date", s.getDate().toString());
          n.put("time", s.getHour().toString().substring(0, 5));
          slots.add(n);
        });

    return slots;
  }

  private void superponerLocations(ObjectNode raiz) {
    List<Stablishment> sedes = stablishmentRepository.findAll();
    if (sedes.isEmpty()) {
      return;
    }
    Branding b = branding();

    ArrayNode items = objectMapper.createArrayNode();
    for (Stablishment s : sedes) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("id", "sede-" + s.getId());
      n.put("name", s.getName());
      n.put("address", nvl(s.getAddress()));
      n.put("phone", b == null ? "" : nvl(b.getPhone()));
      items.add(n);
    }
    raiz.set("items", items);
  }

  /**
   * Las filas son las aseguradoras con convenio. El precio del contrato se
   * conserva: CoveragePlan modela la cobertura por plan y servicio, no un
   * "desde" por aseguradora, y derivar uno solo para la portada daria un numero
   * que no corresponde a ninguna cobertura concreta.
   */
  private void superponerCoverage(ObjectNode raiz) {
    List<Insurer> aseguradoras = insurerRepository.findAll();
    if (aseguradoras.isEmpty()) {
      return;
    }

    ArrayNode plantillaFilas = raiz.withArray("rows");
    JsonNode precioModelo = plantillaFilas.isEmpty() ? null : plantillaFilas.get(0).get("price");

    ArrayNode filas = objectMapper.createArrayNode();
    for (Insurer i : aseguradoras) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("id", "aseguradora-" + i.getId());
      n.put("label", i.getName());
      if (precioModelo != null) {
        n.set("price", precioModelo.deepCopy());
      }
      filas.add(n);
    }
    raiz.set("rows", filas);

    Branding b = branding();
    if (b != null && b.getPhone() != null) {
      JsonNode ctas = raiz.get("ctas");
      if (ctas instanceof ObjectNode c && c.get("phone") instanceof ObjectNode tel) {
        tel.put("label", b.getPhone());
        tel.put("href", "tel:" + b.getPhone().replaceAll("[^0-9+]", ""));
      }
    }
  }

  // ----------------------------------------------------------------- helpers

  /** Las rutas de imagen que ya trae un arreglo del contrato, en orden. */
  private List<String> imagenesDe(ArrayNode items) {
    List<String> fotos = new ArrayList<>();
    for (JsonNode n : items) {
      String img = n.path("image").asText("");
      if (!img.isBlank()) {
        fotos.add(img);
      }
    }
    return fotos;
  }

  private Branding branding() {
    return brandingRepository.findAll().stream().findFirst().orElse(null);
  }

  private ObjectNode plantilla(String seccion) {
    return (ObjectNode) plantillas.computeIfAbsent(seccion, s -> {
      try (InputStream in = new ClassPathResource("landing/" + s + ".json").getInputStream()) {
        return objectMapper.readTree(in);
      } catch (IOException e) {
        throw new RuntimeException("No se pudo leer el contrato de la seccion '" + s + "'");
      }
    });
  }

  private ObjectNode objeto(ObjectNode padre, String campo) {
    JsonNode hijo = padre.get(campo);
    if (hijo instanceof ObjectNode o) {
      return o;
    }
    return padre.putObject(campo);
  }

  private void ponerSiHay(ObjectNode destino, String campo, String valor) {
    if (valor != null && !valor.isBlank()) {
      destino.put(campo, valor);
    }
  }

  private String nvl(String s) {
    return s == null ? "" : s;
  }
}
