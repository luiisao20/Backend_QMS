package com.devluis.types;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OtpData {
  private String otp;
  private LocalDateTime expiresAt;
  private int intentosFallidos;

  public boolean isExpired() {
    return LocalDateTime.now().isAfter(expiresAt);
  }

  public boolean excedioIntentos() {
    return intentosFallidos >= 3;
  }
}
