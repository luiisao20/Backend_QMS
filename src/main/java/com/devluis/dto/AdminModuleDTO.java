package com.devluis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminModuleDTO {
  private Long id;
  private String moduleKey;
  private String label;
  private boolean enabled;
}
