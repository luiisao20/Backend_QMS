package com.devluis.dto;

import java.time.OffsetDateTime;

import com.devluis.types.BlockReasonKind;

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
public class BlockReasonDTO {
  private Long id;

  @NotBlank(message = "El campo del nombre es requerido")
  private String name;

  @NotNull(message = "El campo del tipo es requerido")
  private BlockReasonKind kind;

  private Boolean active;

  private OffsetDateTime createdAt;
}
