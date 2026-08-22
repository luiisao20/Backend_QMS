package com.devluis.types;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecoverPasswordInitBody {
  @NotBlank(message = "El correo es obligatorio")
  @Email(message = "Formato de correo inválido")
  private String email;
}
