package com.devluis.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.devluis.dto.PackageItemDTO;
import com.devluis.dto.ServicePackageDTO;
import com.devluis.dto.ServicioDTO;
import com.devluis.entity.PackageItem;
import com.devluis.entity.ServicePackage;
import com.devluis.entity.Servicio;
import com.devluis.repository.ServicePackageRepository;
import com.devluis.repository.ServiceRepository;
import com.devluis.utils.Money;

import lombok.Data;

@Service
@Data
public class ServicePackageService {
  private final ServicePackageRepository servicePackageRepository;
  private final ServiceRepository serviceRepository;

  public ServicePackageDTO create(ServicePackageDTO dto) {
    ServicePackage servicePackage = ServicePackage.builder()
        .name(dto.getName())
        .description(dto.getDescription())
        .price(dto.getPrice())
        .items(new java.util.ArrayList<>())
        .build();
    servicePackage.getItems().addAll(buildItems(servicePackage, dto.getItems()));

    ServicePackage saved = servicePackageRepository.save(servicePackage);
    return mapToDTO(saved);
  }

  public Page<ServicePackageDTO> getAll(String name, Pageable pageable) {
    if (name != null && !name.trim().isEmpty()) {
      return servicePackageRepository.findByNameContainingIgnoreCase(name.trim(), pageable).map(this::mapToDTO);
    }
    return servicePackageRepository.findAll(pageable).map(this::mapToDTO);
  }

  public Page<ServicePackageDTO> getAll(Pageable pageable) {
    return getAll(null, pageable);
  }

  public ServicePackageDTO getById(Long id) {
    return mapToDTO(findByIdOrThrow(id));
  }

  // Full replace of the item list on every update — the admin form
  // resubmits the whole bundle each time. Mutates the EXISTING managed
  // collection in place (clear + addAll) instead of assigning a brand-new
  // List reference, which is the safe idiom for a
  // cascade=ALL/orphanRemoval=true mappedBy collection (replacing the Java
  // reference outright is a well-known way to confuse Hibernate's orphan
  // tracking) — UNVERIFIED against a real database, see apply report.
  public ServicePackageDTO update(Long id, ServicePackageDTO dto) {
    ServicePackage servicePackage = findByIdOrThrow(id);
    servicePackage.setName(dto.getName());
    servicePackage.setDescription(dto.getDescription());
    servicePackage.setPrice(dto.getPrice());

    servicePackage.getItems().clear();
    servicePackage.getItems().addAll(buildItems(servicePackage, dto.getItems()));

    ServicePackage updated = servicePackageRepository.save(servicePackage);
    return mapToDTO(updated);
  }

  public void delete(Long id) {
    if (!servicePackageRepository.existsById(id)) {
      throw new RuntimeException("Paquete no encontrado");
    }
    servicePackageRepository.deleteById(id);
  }

  private ServicePackage findByIdOrThrow(Long id) {
    return servicePackageRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Paquete no encontrado"));
  }

  private List<PackageItem> buildItems(ServicePackage servicePackage, List<PackageItemDTO> itemDtos) {
    return itemDtos.stream().map(itemDto -> {
      Servicio servicio = serviceRepository.findById(itemDto.getServicio().getId())
          .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));
      return PackageItem.builder()
          .servicePackage(servicePackage)
          .servicio(servicio)
          .quantity(itemDto.getQuantity())
          .build();
    }).collect(Collectors.toList());
  }

  // "a package's total against its items": sum of each line item's Servicio
  // NET price (price - discount, via Money.netPrice — never the raw list
  // price) times its quantity. Independent of ServicePackage.price, which
  // is an admin-set number, not derived from this.
  private BigDecimal computeItemsTotal(ServicePackage servicePackage) {
    List<PackageItem> items = servicePackage.getItems() != null ? servicePackage.getItems() : List.of();
    return Money.of(items.stream()
        .map(item -> Money.netPrice(item.getServicio()).multiply(BigDecimal.valueOf(item.getQuantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  private ServicePackageDTO mapToDTO(ServicePackage entity) {
    List<PackageItem> entityItems = entity.getItems() != null ? entity.getItems() : List.of();
    List<PackageItemDTO> itemDtos = entityItems.stream()
        .map(item -> PackageItemDTO.builder()
            .id(item.getId())
            .servicio(ServicioDTO.builder()
                .id(item.getServicio().getId())
                .name(item.getServicio().getName())
                .price(item.getServicio().getPrice())
                .discount(item.getServicio().getDiscount())
                .build())
            .quantity(item.getQuantity())
            .build())
        .collect(Collectors.toList());

    BigDecimal itemsTotal = computeItemsTotal(entity);
    BigDecimal savings = itemsTotal.subtract(Money.of(entity.getPrice()));

    return ServicePackageDTO.builder()
        .id(entity.getId())
        .name(entity.getName())
        .description(entity.getDescription())
        .price(entity.getPrice())
        .items(itemDtos)
        .itemsTotal(itemsTotal)
        .savings(savings)
        .createdAt(entity.getCreatedAt())
        .build();
  }
}
