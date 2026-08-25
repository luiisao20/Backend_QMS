package com.devluis.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.devluis.dto.AdminModuleDTO;
import com.devluis.entity.AdminModule;
import com.devluis.repository.AdminModuleRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

/**
 * Storage + read/write API for enabling or disabling admin panel
 * destinations.
 *
 * <p>
 * HONESTY NOTE (full write-up in the apply report): this is DATA ONLY
 * today. Nothing reads {@code enabled} yet — the Angular admin panel still
 * generates its nav and its route table from the static {@code ADMIN_NAV}
 * constant at compile time, and no backend endpoint checks this table
 * before serving a request. Toggling a module here changes what
 * {@code GET /api/admin-modules} reports; it does not hide any menu entry,
 * block any Angular route, or gate any other endpoint yet.
 */
@Service
@RequiredArgsConstructor
public class AdminModuleService {

  // Manual mirror of the 12 top-level entries in ADMIN_NAV
  // (clinicore-angular/src/app/features/admin/admin-nav.data.ts) as of
  // 2026-08-25. Deliberately the 12 top-level groups, NOT the 32 leaf
  // destinations: a whole nav group ("Precios", "Finanzas"...) is the unit
  // an admin thinks of as "a feature to turn off", and staying at 12 keeps
  // this list human-reviewable. There is no automated check tying this to
  // ADMIN_NAV (unlike admin.routes.spec.ts on the Angular side) — see the
  // entity's own docblock for the drift risk this accepts.
  //
  // A LinkedHashMap (not Map.of/Map.ofEntries) on purpose: the JDK's
  // immutable Map.of family does NOT preserve insertion order — its
  // iteration order is intentionally randomized per JVM run — which would
  // make the seed order (and therefore getAll()'s default order) silently
  // unstable.
  static final Map<String, String> SEED;

  static {
    Map<String, String> seed = new LinkedHashMap<>();
    seed.put("dashboard", "Dashboard");
    seed.put("metricas", "Métricas");
    seed.put("modulos", "Módulos");
    seed.put("personalizacion", "Personalización");
    seed.put("administracion", "Admin");
    seed.put("precios", "Precios");
    seed.put("bloqueo", "Bloqueo de citas");
    seed.put("turnos", "Turnos");
    seed.put("calendario", "Calendario");
    seed.put("pacientes", "Pacientes");
    seed.put("finanzas", "Finanzas");
    seed.put("reportes", "Reportes");
    SEED = Collections.unmodifiableMap(seed);
  }

  // The one module that must never end up disabled: it is this feature's
  // own on/off screen. Disabling it would not currently lock an admin out
  // of anything (nothing enforces this table yet), but the guard is added
  // now, before enforcement exists, specifically so enforcement can never
  // be wired later without this self-lockout hole already being closed.
  static final String SELF_KEY = "modulos";

  private final AdminModuleRepository adminModuleRepository;

  public List<AdminModuleDTO> getAll() {
    List<String> declaredOrder = new ArrayList<>(SEED.keySet());
    return seedIfEmpty().stream()
        .map(this::mapToDTO)
        .sorted(Comparator.comparingInt(dto -> declaredOrder.indexOf(dto.getModuleKey())))
        .collect(Collectors.toList());
  }

  public AdminModuleDTO setEnabled(String moduleKey, boolean enabled) {
    if (SELF_KEY.equals(moduleKey) && !enabled) {
      throw new RuntimeException("El módulo de gestión de módulos no se puede deshabilitar");
    }

    AdminModule module = seedIfEmpty().stream()
        .filter(m -> m.getModuleKey().equals(moduleKey))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Módulo no encontrado: " + moduleKey));

    module.setEnabled(enabled);
    return mapToDTO(adminModuleRepository.save(module));
  }

  // Lazy seed: populates the fixed catalog on first read/write instead of
  // requiring a startup hook (CommandLineRunner) or a migration tool —
  // neither exists in this project (ddl-auto=update, no Flyway/Liquibase) —
  // and this keeps the guarantee unit-testable with plain Mockito instead
  // of a Spring context this project cannot boot without a live datasource.
  private List<AdminModule> seedIfEmpty() {
    List<AdminModule> existing = adminModuleRepository.findAll();
    if (!existing.isEmpty()) {
      return existing;
    }

    List<AdminModule> seeded = SEED.entrySet().stream()
        .map(entry -> AdminModule.builder()
            .moduleKey(entry.getKey())
            .label(entry.getValue())
            .enabled(true)
            .build())
        .collect(Collectors.toList());

    return adminModuleRepository.saveAll(seeded);
  }

  private AdminModuleDTO mapToDTO(AdminModule entity) {
    return AdminModuleDTO.builder()
        .id(entity.getId())
        .moduleKey(entity.getModuleKey())
        .label(entity.getLabel())
        .enabled(entity.isEnabled())
        .build();
  }
}
