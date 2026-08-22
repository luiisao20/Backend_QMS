package com.devluis.types;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordBody {
  @NotBlank(message = "La contraseña es obligatoria")
  private String password;
  
  @NotBlank(message = "Debes repetir la contraseña")
  private String repeatedPassword;
}
