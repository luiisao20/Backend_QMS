package com.devluis.dto;

import java.util.UUID;

import com.devluis.types.Role;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OperatorDTO {
    private UUID uuid;

    @Email(message = "El correo no es válido")
    @NotBlank(message = "El correo es requerido")
    private String email;

    private String password;

    @NotBlank(message = "El nombre es requerido")
    private String firstName;

    @NotBlank(message = "El apellido es requerido")
    private String lastName;

    @NotNull(message = "El rol es requerido")
    private Role role;

    private StablishmentDTO stablishment;
}
