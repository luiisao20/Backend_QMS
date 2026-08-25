package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.devluis.dto.PackageItemDTO;
import com.devluis.dto.ServicePackageDTO;
import com.devluis.dto.ServicioDTO;
import com.devluis.entity.PackageItem;
import com.devluis.entity.ServicePackage;
import com.devluis.entity.Servicio;
import com.devluis.repository.ServicePackageRepository;
import com.devluis.repository.ServiceRepository;

/**
 * "a package's total against its items" (required coverage): itemsTotal
 * must equal the sum of each line item's Servicio NET price (price -
 * discount, via Money.netPrice) times its quantity — never the raw list
 * price, and never the package's own `price`, which is an independent,
 * admin-set number (see ServicePackage's docblock).
 */
@ExtendWith(MockitoExtension.class)
class ServicePackageServiceTest {

  @Mock
  private ServicePackageRepository servicePackageRepository;
  @Mock
  private ServiceRepository serviceRepository;

  private ServicePackageService servicePackageService;

  @BeforeEach
  void setUp() {
    servicePackageService = new ServicePackageService(servicePackageRepository, serviceRepository);
  }

  private Servicio cleaning() {
    return Servicio.builder().id(1L).name("Limpieza dental").price(50f).discount(10f).build();
  }

  private Servicio whitening() {
    return Servicio.builder().id(2L).name("Blanqueamiento").price(100f).discount(null).build();
  }

  private ServicePackageDTO validDto() {
    return ServicePackageDTO.builder()
        .name("Paquete Sonrisa")
        .description("Limpieza + blanqueamiento")
        .price(new BigDecimal("120.00"))
        .items(List.of(
            PackageItemDTO.builder().servicio(ServicioDTO.builder().id(1L).build()).quantity(1).build(),
            PackageItemDTO.builder().servicio(ServicioDTO.builder().id(2L).build()).quantity(1).build()))
        .build();
  }

  @Test
  void create_savesPackage_andComputesItemsTotalFromNetPricesTimesQuantity() {
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(cleaning()));
    when(serviceRepository.findById(2L)).thenReturn(Optional.of(whitening()));
    when(servicePackageRepository.save(any(ServicePackage.class))).thenAnswer(inv -> {
      ServicePackage p = inv.getArgument(0);
      p.setId(9L);
      return p;
    });

    ServicePackageDTO result = servicePackageService.create(validDto());

    assertThat(result.getId()).isEqualTo(9L);
    // cleaning net price 40.00 * 1 + whitening net price 100.00 * 1 = 140.00
    assertThat(result.getItemsTotal()).isEqualByComparingTo("140.00");
    assertThat(result.getItems()).hasSize(2);
  }

  @Test
  void create_computesSavings_asItemsTotalMinusPrice() {
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(cleaning()));
    when(serviceRepository.findById(2L)).thenReturn(Optional.of(whitening()));
    when(servicePackageRepository.save(any(ServicePackage.class))).thenAnswer(inv -> inv.getArgument(0));

    ServicePackageDTO result = servicePackageService.create(validDto());

    // itemsTotal 140.00 - price 120.00 = 20.00
    assertThat(result.getSavings()).isEqualByComparingTo("20.00");
  }

  @Test
  void create_allowsNegativeSavings_whenPriceExceedsItemsTotal_withoutRejectingIt() {
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(cleaning()));
    when(serviceRepository.findById(2L)).thenReturn(Optional.of(whitening()));
    when(servicePackageRepository.save(any(ServicePackage.class))).thenAnswer(inv -> inv.getArgument(0));
    ServicePackageDTO dto = validDto();
    dto.setPrice(new BigDecimal("999.00"));

    ServicePackageDTO result = servicePackageService.create(dto);

    assertThat(result.getSavings()).isEqualByComparingTo("-859.00");
  }

  @Test
  void create_multipliesNetPriceByQuantity_notJustCountingLines() {
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(cleaning()));
    when(servicePackageRepository.save(any(ServicePackage.class))).thenAnswer(inv -> inv.getArgument(0));
    ServicePackageDTO dto = ServicePackageDTO.builder()
        .name("3x Limpieza").price(new BigDecimal("100.00"))
        .items(List.of(PackageItemDTO.builder()
            .servicio(ServicioDTO.builder().id(1L).build()).quantity(3).build()))
        .build();

    ServicePackageDTO result = servicePackageService.create(dto);

    // cleaning net price 40.00 * 3 = 120.00
    assertThat(result.getItemsTotal()).isEqualByComparingTo("120.00");
  }

  @Test
  void create_throws_whenAnItemsServicioDoesNotExist() {
    when(serviceRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicePackageService.create(validDto()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Servicio no encontrado");

    verify(servicePackageRepository, never()).save(any());
  }

  @Test
  void getAll_withNameFilter_delegatesToFindByNameContainingIgnoreCase() {
    Pageable pageable = PageRequest.of(0, 10);
    ServicePackage entity = ServicePackage.builder().id(1L).name("Paquete Sonrisa")
        .price(new BigDecimal("120.00")).items(List.of()).build();
    when(servicePackageRepository.findByNameContainingIgnoreCase(eq("Sonrisa"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entity)));

    Page<ServicePackageDTO> result = servicePackageService.getAll("Sonrisa", pageable);

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  void getAll_withoutFilter_delegatesToFindAll() {
    Pageable pageable = PageRequest.of(0, 10);
    when(servicePackageRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

    Page<ServicePackageDTO> result = servicePackageService.getAll(pageable);

    assertThat(result.getContent()).isEmpty();
  }

  @Test
  void getById_returnsMappedDTO_whenFound() {
    ServicePackage entity = ServicePackage.builder().id(1L).name("Paquete Sonrisa")
        .price(new BigDecimal("120.00"))
        .items(List.of(PackageItem.builder().id(1L).servicio(cleaning()).quantity(2).build()))
        .build();
    when(servicePackageRepository.findById(1L)).thenReturn(Optional.of(entity));

    ServicePackageDTO result = servicePackageService.getById(1L);

    assertThat(result.getName()).isEqualTo("Paquete Sonrisa");
    // cleaning net price 40.00 * 2 = 80.00
    assertThat(result.getItemsTotal()).isEqualByComparingTo("80.00");
  }

  @Test
  void getById_throws_whenNotFound() {
    when(servicePackageRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicePackageService.getById(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrado");
  }

  @Test
  void update_replacesTheEntireItemList() {
    ServicePackage existing = ServicePackage.builder().id(1L).name("Vieja")
        .price(new BigDecimal("50.00"))
        .items(new java.util.ArrayList<>(
            List.of(PackageItem.builder().id(1L).servicio(cleaning()).quantity(1).build())))
        .build();
    when(servicePackageRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(serviceRepository.findById(2L)).thenReturn(Optional.of(whitening()));
    when(servicePackageRepository.save(any(ServicePackage.class))).thenAnswer(inv -> inv.getArgument(0));

    ServicePackageDTO dto = ServicePackageDTO.builder()
        .name("Nueva").price(new BigDecimal("90.00"))
        .items(List.of(PackageItemDTO.builder()
            .servicio(ServicioDTO.builder().id(2L).build()).quantity(1).build()))
        .build();

    ServicePackageDTO result = servicePackageService.update(1L, dto);

    assertThat(result.getName()).isEqualTo("Nueva");
    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItemsTotal()).isEqualByComparingTo("100.00");
  }

  @Test
  void update_throws_whenNotFound() {
    when(servicePackageRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicePackageService.update(404L, validDto()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrado");
  }

  @Test
  void delete_removesPackage_whenExists() {
    when(servicePackageRepository.existsById(1L)).thenReturn(true);

    servicePackageService.delete(1L);

    verify(servicePackageRepository).deleteById(1L);
  }

  @Test
  void delete_throws_whenNotFound() {
    when(servicePackageRepository.existsById(404L)).thenReturn(false);

    assertThatThrownBy(() -> servicePackageService.delete(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrado");

    verify(servicePackageRepository, never()).deleteById(any());
  }
}
