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
public class InitRegistrationBody {
  @Email(message = "El correo no es válido")
  @NotBlank(message = "El correo es requerido")
  private String email;

  @Pattern(regexp = "^[0-9]{10}$", message = "La cédula debe contener exactamente 10 dígitos numéricos.")
  @NotBlank(message = "La cédula es requerida")
  private String ci;
}
