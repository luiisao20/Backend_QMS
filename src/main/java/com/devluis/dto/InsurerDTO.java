package com.devluis.dto;

import java.time.OffsetDateTime;

import com.devluis.types.InsurerType;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InsurerDTO {
  private Long id;

  @NotBlank(message = "El nombre de la aseguradora es requerido")
  private String name;

  @NotNull(message = "El tipo de aseguradora (privada o pública) es requerido")
  private InsurerType type;

  private OffsetDateTime createdAt;
}
