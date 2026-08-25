package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devluis.dto.BrandingDTO;
import com.devluis.entity.Branding;
import com.devluis.repository.BrandingRepository;

@ExtendWith(MockitoExtension.class)
class BrandingServiceTest {

  @Mock
  private BrandingRepository brandingRepository;

  private BrandingService brandingService;

  @BeforeEach
  void setUp() {
    brandingService = new BrandingService(brandingRepository);
  }

  @Test
  void get_returnsEmptyDto_whenNoRowExists() {
    when(brandingRepository.findAll()).thenReturn(Collections.emptyList());

    BrandingDTO result = brandingService.get();

    assertThat(result.getId()).isNull();
    assertThat(result.getName()).isNull();
  }

  @Test
  void get_returnsMappedDto_whenRowExists() {
    Branding existing = Branding.builder()
        .id(1L)
        .name("Clínica San Rafael")
        .primaryColor("#1A2B3C")
        .email("contacto@sanrafael.ec")
        .build();
    when(brandingRepository.findAll()).thenReturn(List.of(existing));

    BrandingDTO result = brandingService.get();

    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getName()).isEqualTo("Clínica San Rafael");
    assertThat(result.getPrimaryColor()).isEqualTo("#1A2B3C");
  }

  @Test
  void save_insertsNewRow_whenNoneExists() {
    when(brandingRepository.findAll()).thenReturn(Collections.emptyList());
    // The id-at-invocation-time is captured into a local var INSIDE the
    // answer, before it mutates the argument in place — an ArgumentCaptor
    // would not work here, because it holds a reference to the same
    // instance the answer mutates, so by the time an assertion runs after
    // save() returns, captor.getValue().getId() would already reflect the
    // post-mutation state (1L), not the state save() was actually called
    // with. This mirrors what a real IDENTITY-generated id does: null going
    // in, assigned coming out.
    List<Long> idsSeenBySave = new java.util.ArrayList<>();
    when(brandingRepository.save(any(Branding.class))).thenAnswer(inv -> {
      Branding b = inv.getArgument(0);
      idsSeenBySave.add(b.getId());
      b.setId(1L);
      return b;
    });

    BrandingDTO dto = BrandingDTO.builder().name("Clínica San Rafael").build();
    BrandingDTO result = brandingService.save(dto);

    // Insert path: the entity handed to save() had no id yet.
    assertThat(idsSeenBySave).containsExactly((Long) null);
    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getName()).isEqualTo("Clínica San Rafael");
  }

  @Test
  void save_updatesExistingRow_ratherThanInserting_onSecondWrite() {
    Branding existing = Branding.builder().id(7L).name("Nombre viejo").build();
    when(brandingRepository.findAll()).thenReturn(List.of(existing));
    when(brandingRepository.save(any(Branding.class))).thenAnswer(inv -> inv.getArgument(0));

    BrandingDTO dto = BrandingDTO.builder().name("Nombre nuevo").primaryColor("#FFFFFF").build();
    BrandingDTO result = brandingService.save(dto);

    ArgumentCaptor<Branding> captor = ArgumentCaptor.forClass(Branding.class);
    verify(brandingRepository).save(captor.capture());
    // The whole point of the singleton: the SAME id is reused (an update via
    // merge()), never a second row.
    assertThat(captor.getValue().getId()).isEqualTo(7L);
    assertThat(result.getId()).isEqualTo(7L);
    assertThat(result.getName()).isEqualTo("Nombre nuevo");
    assertThat(result.getPrimaryColor()).isEqualTo("#FFFFFF");
  }

  @Test
  void save_ignoresClientSuppliedId_keepingTheExistingRowsIdentity() {
    Branding existing = Branding.builder().id(7L).name("Nombre viejo").build();
    when(brandingRepository.findAll()).thenReturn(List.of(existing));
    when(brandingRepository.save(any(Branding.class))).thenAnswer(inv -> inv.getArgument(0));

    // A caller trying to smuggle a different id in the body must not be able
    // to fork a second row or hijack a different one.
    BrandingDTO dto = BrandingDTO.builder().id(999L).name("Nombre nuevo").build();
    BrandingDTO result = brandingService.save(dto);

    assertThat(result.getId()).isEqualTo(7L);
  }
}
