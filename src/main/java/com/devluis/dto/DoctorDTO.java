package com.devluis.dto;

import java.util.UUID;
import com.devluis.types.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorDTO {
    private UUID uuid;

    @Email(message = "El correo no es válido")
    @NotBlank(message = "El correo es requerido")
    private String email;

    private String password;

    @NotBlank(message = "El nombre es requerido")
    private String firstName;

    @NotBlank(message = "El apellido es requerido")
    private String lastName;

    /**
     * La especialidad como texto libre.
     *
     * Ya NO lleva @NotBlank: desde que existe el catálogo de especialidades, un
     * cliente puede mandar specialityId en su lugar y el servicio copia el
     * nombre desde ahí. Que llegue exactamente uno de los dos lo valida
     * DoctorService.resolveSpeciality, porque una anotación de campo no puede
     * expresar "uno u otro".
     *
     * Los clientes que ya existen mandan solo este campo y siguen funcionando.
     */
    private String speciality;

    /**
     * Id de la fila del catálogo de especialidades.
     *
     * Cuando viene, GANA sobre el texto: el servicio resuelve el nombre desde el
     * catálogo y lo copia al campo de arriba, así que las dos columnas no pueden
     * quedar diciendo cosas distintas.
     */
    private Long specialityId;

    private Gender gender;

    @NotBlank(message = "La cédula es requerida")
    @Pattern(regexp = "^[0-9]{10}$", message = "La cédula debe contener exactamente 10 dígitos numéricos.")
    private String ci;

    private java.util.List<StablishmentDTO> stablishments;
    
    private java.util.List<ServicioDTO> services;
}
