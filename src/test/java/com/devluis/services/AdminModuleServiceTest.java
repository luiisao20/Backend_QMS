package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devluis.dto.AdminModuleDTO;
import com.devluis.entity.AdminModule;
import com.devluis.repository.AdminModuleRepository;

@ExtendWith(MockitoExtension.class)
class AdminModuleServiceTest {

  @Mock
  private AdminModuleRepository adminModuleRepository;

  private AdminModuleService adminModuleService;

  @BeforeEach
  void setUp() {
    adminModuleService = new AdminModuleService(adminModuleRepository);
  }

  @Test
  void getAll_seedsTheFixedCatalog_whenTableIsEmpty() {
    when(adminModuleRepository.findAll()).thenReturn(Collections.emptyList());
    when(adminModuleRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    List<AdminModuleDTO> result = adminModuleService.getAll();

    assertThat(result).hasSize(12);
    assertThat(result).extracting(AdminModuleDTO::getModuleKey)
        .contains("dashboard", "metricas", "modulos", "personalizacion", "administracion",
            "precios", "bloqueo", "turnos", "calendario", "pacientes", "finanzas", "reportes");
    assertThat(result).allMatch(AdminModuleDTO::isEnabled);
    verify(adminModuleRepository).saveAll(anyList());
  }

  @Test
  void getAll_doesNotReseed_whenCatalogAlreadyExists() {
    AdminModule existing = AdminModule.builder()
        .id(1L).moduleKey("dashboard").label("Dashboard").enabled(true).build();
    when(adminModuleRepository.findAll()).thenReturn(List.of(existing));

    List<AdminModuleDTO> result = adminModuleService.getAll();

    assertThat(result).hasSize(1);
    verify(adminModuleRepository, never()).saveAll(anyList());
  }

  @Test
  void getAll_returnsModulesInAdminNavDeclaredOrder() {
    when(adminModuleRepository.findAll()).thenReturn(Collections.emptyList());
    when(adminModuleRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    List<AdminModuleDTO> result = adminModuleService.getAll();

    assertThat(result).extracting(AdminModuleDTO::getModuleKey)
        .containsExactly("dashboard", "metricas", "modulos", "personalizacion", "administracion",
            "precios", "bloqueo", "turnos", "calendario", "pacientes", "finanzas", "reportes");
  }

  @Test
  void setEnabled_disablesAModule_whenNotSelf() {
    AdminModule precios = AdminModule.builder()
        .id(6L).moduleKey("precios").label("Precios").enabled(true).build();
    when(adminModuleRepository.findAll()).thenReturn(List.of(precios));
    when(adminModuleRepository.save(any(AdminModule.class))).thenAnswer(inv -> inv.getArgument(0));

    AdminModuleDTO result = adminModuleService.setEnabled("precios", false);

    assertThat(result.isEnabled()).isFalse();
  }

  @Test
  void setEnabled_throwsClearSpanishMessage_whenModuleKeyUnknown() {
    when(adminModuleRepository.findAll()).thenReturn(Collections.emptyList());
    when(adminModuleRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    assertThatThrownBy(() -> adminModuleService.setEnabled("no-existe", false))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrado");
  }

  @Test
  void setEnabled_throwsAndNeverSaves_whenDisablingModulosItself() {
    assertThatThrownBy(() -> adminModuleService.setEnabled("modulos", false))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no se puede deshabilitar");

    verify(adminModuleRepository, never()).save(any(AdminModule.class));
  }

  @Test
  void setEnabled_allowsEnablingModulosItself() {
    AdminModule modulos = AdminModule.builder()
        .id(3L).moduleKey("modulos").label("Módulos").enabled(false).build();
    when(adminModuleRepository.findAll()).thenReturn(List.of(modulos));
    when(adminModuleRepository.save(any(AdminModule.class))).thenAnswer(inv -> inv.getArgument(0));

    AdminModuleDTO result = adminModuleService.setEnabled("modulos", true);

    assertThat(result.isEnabled()).isTrue();
  }
}
