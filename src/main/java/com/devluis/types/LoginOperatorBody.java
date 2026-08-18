package com.devluis.types;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginOperatorBody {
  @Email(message = "El correo no es válido")
  @NotBlank(message = "El correo es requerido")
  private String email;

  @NotBlank(message = "La contraseña es requerida")
  private String password;
}
