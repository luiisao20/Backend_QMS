package com.devluis.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.devluis.types.ClinicalResourceType;
import com.devluis.types.Role;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClinicalAccessLogDTO {
  private Long id;

  private UUID patientUuid;

  private UUID accessedByUuid;

  private Role accessedByRole;

  private ClinicalResourceType resourceType;

  private Long resourceId;

  private OffsetDateTime accessedAt;
}
