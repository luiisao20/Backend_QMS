package com.devluis.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// "precios/paquetes": a bundle of services sold at one combined price. Named
// ServicePackage (not "Package") to avoid shadowing java.lang.Package.
//
// `price` is set explicitly by the admin, NEVER auto-summed from the item
// list — a bundle price is a business decision (usually a discount off
// buying the services separately), not a derived number. PackageService
// separately computes `itemsTotal` (the sum of each line item's Servicio
// net price * quantity) purely for display/comparison — see
// ServicePackageDTO. Whether `price` ends up above or below `itemsTotal` is
// NOT validated here: deliberately left unenforced (see apply report) so an
// admin's real intent is always visible instead of silently rejected.
//
// No `active` flag: same minimalism as BlockReason — there is no purchase/
// order entity in this codebase yet to give "active/inactive" an enforced
// meaning, so adding one now would be exactly the unenforced-field trap the
// task warned against.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "service_packages")
@Entity
public class ServicePackage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  private String description;

  // BigDecimal on purpose (unlike Servicio.price/discount, which are Float):
  // brand-new field, no legacy constraint, same reasoning as
  // CoveragePlan.copayAmount.
  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price;

  // A package is not a single number — these are its line items. Full
  // replace-on-update (see ServicePackageService): the admin form resubmits
  // the whole item list every time, simplest correct model for a bundle
  // builder with no incremental-item-CRUD requirement.
  @OneToMany(mappedBy = "servicePackage", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<PackageItem> items = new java.util.ArrayList<>();

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
