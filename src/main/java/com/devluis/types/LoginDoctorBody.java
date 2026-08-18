package com.devluis.types;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginDoctorBody {
  @Email(message = "El correo no es válido")
  private String email;

  @Pattern(regexp = "^[0-9]{10}$", message = "La cédula debe contener exactamente 10 dígitos numéricos.")
  private String ci;

  @NotBlank(message = "La contraseña es requerida")
  private String password;
}
