package com.devluis.services;

import com.devluis.dto.BrandingDTO;
import com.devluis.entity.Branding;
import com.devluis.repository.BrandingRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BrandingService {
  private final BrandingRepository brandingRepository;

  // No row yet = branding was never configured. Returned as an
  // (almost) empty DTO rather than a 404/exception: a public landing page
  // must be able to render even before an admin has filled anything in, and
  // @JsonInclude(NON_NULL) on BrandingDTO keeps the response honest by
  // simply omitting whatever hasn't been set instead of inventing
  // placeholder content server-side.
  public BrandingDTO get() {
    return brandingRepository.findAll().stream()
        .findFirst()
        .map(this::mapToDTO)
        .orElseGet(() -> BrandingDTO.builder().build());
  }

  // Singleton upsert: reuses the one existing row if present (save() on an
  // entity with a non-null id merges/updates instead of inserting — same
  // idiom every other *Service#update in this codebase already uses),
  // otherwise creates the first and only row. There is no separate
  // create()/delete() pair on purpose: branding has exactly one instance
  // for the life of this clinic.
  public BrandingDTO save(BrandingDTO dto) {
    Branding branding = brandingRepository.findAll().stream()
        .findFirst()
        .orElseGet(Branding::new);

    branding.setName(dto.getName());
    branding.setLogoUrl(dto.getLogoUrl());
    branding.setPrimaryColor(dto.getPrimaryColor());
    branding.setSecondaryColor(dto.getSecondaryColor());
    branding.setPhone(dto.getPhone());
    branding.setEmergencyPhone(dto.getEmergencyPhone());
    branding.setWhatsapp(dto.getWhatsapp());
    branding.setEmail(dto.getEmail());

    return mapToDTO(brandingRepository.save(branding));
  }

  private BrandingDTO mapToDTO(Branding entity) {
    return BrandingDTO.builder()
        .id(entity.getId())
        .name(entity.getName())
        .logoUrl(entity.getLogoUrl())
        .primaryColor(entity.getPrimaryColor())
        .secondaryColor(entity.getSecondaryColor())
        .phone(entity.getPhone())
        .emergencyPhone(entity.getEmergencyPhone())
        .whatsapp(entity.getWhatsapp())
        .email(entity.getEmail())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
