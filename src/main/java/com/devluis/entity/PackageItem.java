package com.devluis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// One line item of a ServicePackage: "quantity" units of one Servicio. A
// genuine child/association entity (not a plain @ManyToMany) precisely
// because "how many" needs somewhere to live — see ServicePackage's
// docblock.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "package_items")
@Entity
public class PackageItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "package_id", nullable = false)
  private ServicePackage servicePackage;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "service_id", nullable = false)
  private Servicio servicio;

  @Column(nullable = false)
  private Integer quantity;
}
