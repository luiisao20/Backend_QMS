package com.devluis.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// `id` and `updatedAt` are read-only, echoed back on write — BrandingService
// never reads them off an incoming body (see its own docblock for why: the
// client must never be able to pick which row gets updated).
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BrandingDTO {
  private Long id;

  @NotBlank(message = "El nombre de la clínica es requerido")
  private String name;

  private String logoUrl;

  @Pattern(regexp = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$",
      message = "El color primario debe ser un código hexadecimal válido (ej. #1A2B3C)")
  private String primaryColor;

  @Pattern(regexp = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$",
      message = "El color secundario debe ser un código hexadecimal válido (ej. #1A2B3C)")
  private String secondaryColor;

  private String phone;

  private String emergencyPhone;

  private String whatsapp;

  @Email(message = "El correo de contacto no tiene un formato válido")
  private String email;

  private OffsetDateTime updatedAt;
}
