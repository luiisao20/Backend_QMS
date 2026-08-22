package com.devluis.types;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyOtpBody {
  @NotBlank(message = "El código OTP es obligatorio")
  private String otp;
}
