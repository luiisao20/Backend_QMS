package com.devluis.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StablishmentDTO {

  private Long id;

  @NotBlank(message = "El campo del nombre es requerido")
  private String name;

  @NotBlank(message = "El campo de la dirección es requerido")
  private String address;

  private List<OperatorDTO> operators;

  private List<DoctorDTO> doctors;

}
