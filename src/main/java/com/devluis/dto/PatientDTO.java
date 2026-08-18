package com.devluis.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.devluis.types.Gender;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientDTO {
    private UUID uuid;

    @Email(message = "El correo no es válido")
    @NotBlank(message = "El correo es requerido")
    private String email;

    @NotBlank(message = "La contraseña es requerida")
    private String password;

    @NotBlank(message = "El nombre es requerido")
    private String firstName;

    @NotBlank(message = "El apellido es requerido")
    private String lastName;

    @NotBlank(message = "La cédula es requerida")
    @Pattern(regexp = "^[0-9]{10}$", message = "La cédula debe contener exactamente 10 dígitos numéricos.")
    private String ci;

    @NotNull(message = "La fecha de nacimiento es requerida")
    private LocalDate birthday;

    private Gender gender;
    private String address;
    private String phone;
    private String emergencyContactPhone;
    private String emergencyContactName;
}
