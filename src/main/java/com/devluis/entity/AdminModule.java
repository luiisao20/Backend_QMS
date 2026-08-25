package com.devluis.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// One row per top-level destination in the Angular admin panel's ADMIN_NAV
// (clinicore-angular/src/app/features/admin/admin-nav.data.ts) — dashboard,
// metricas, modulos, personalizacion, administracion, precios, bloqueo,
// turnos, calendario, pacientes, finanzas, reportes. `moduleKey` mirrors
// that constant's own `id` field 1:1 on purpose, so a future Angular
// consumer needs no translation table between the two. The catalog is
// seeded lazily by AdminModuleService — see AdminModuleService.SEED — not
// created here or via a migration script (this project has none).
//
// HONESTY NOTE (full write-up in the apply report): this table and its
// `enabled` flag are NOT wired to anything yet. ADMIN_NAV is still a
// static, compile-time TypeScript array, asserted exhaustive by
// admin.routes.spec.ts — disabling a row here today does not hide the menu
// entry, does not block the Angular route, and does not gate any backend
// endpoint. This is deliberately scoped as storage + API only; wiring real
// enforcement (both the Angular nav/route guard and, separately, gating the
// real backend endpoints each module represents) is future work, named
// explicitly here so nobody mistakes this table for a working feature-flag
// switch today.
//
// DRIFT RISK: this list is a manually-maintained mirror of ADMIN_NAV, with
// no automated check tying the two together (unlike admin.routes.spec.ts on
// the Angular side, which asserts ADMIN_NAV against the generated route
// table). If ADMIN_NAV's top-level entries change and this list is not
// updated by hand, they will silently drift apart.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "admin_modules")
@Entity
public class AdminModule {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String moduleKey;

  @Column(nullable = false)
  private String label;

  @Column(nullable = false)
  private boolean enabled;

  @UpdateTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime updatedAt;
}
