package com.devluis.services;

import com.devluis.dto.OperatorDTO;
import com.devluis.entity.Operator;
import com.devluis.repository.OperatorRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.Data;
import java.util.UUID;
import java.util.Optional;

@Service
@Data
public class OperatorService implements UserDetailsService {
  private final OperatorRepository operatorRepository;
  private final PasswordEncoder passwordEncoder;
  private final com.devluis.repository.StablishmentRepository stablishmentRepository;
  private final com.devluis.repository.TurnRepository turnRepository;

  public Authentication loginEmail(String email, String password) {
    try {
      UserDetails userDetails = loadUserByUsername(email);

      if (passwordEncoder.matches(password, userDetails.getPassword())) {
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
      }
      throw new BadCredentialsException("Error de autenticación");
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public UserDetails loadUserByUsername(String email) {
    Operator operator = operatorRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

    return User.builder()
        .username(operator.getUuid().toString())
        .password(operator.getPassword())
        .authorities(operator.getRole().name())
        .build();
  }

  public Optional<Operator> findByEmail(String email) {
    return operatorRepository.findByEmail(email);
  }

  public OperatorDTO register(OperatorDTO dto) {
    if (operatorRepository.findByEmail(dto.getEmail()).isPresent()) {
      throw new RuntimeException("Email ya registrado");
    }

    Operator operator = mapToEntity(dto);
    operator.setEmail(dto.getEmail().toLowerCase());
    operator.setPassword(passwordEncoder.encode(dto.getPassword()));
    Operator saved = operatorRepository.save(operator);
    return mapToDTO(saved);
  }

  public OperatorDTO updateOperator(UUID id, OperatorDTO dto) {
    Operator operator = operatorRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Operador no encontrado"));

    operator.setFirstName(dto.getFirstName());
    operator.setLastName(dto.getLastName());
    
    if (dto.getRole() != null) {
      operator.setRole(dto.getRole());
    }

    if (!operator.getEmail().equals(dto.getEmail())) {
      if (operatorRepository.findByEmail(dto.getEmail()).isPresent()) {
        throw new RuntimeException("Email ya registrado");
      }
      operator.setEmail(dto.getEmail());
    }

    if (dto.getPassword() != null && !dto.getPassword().trim().isEmpty()) {
      operator.setPassword(passwordEncoder.encode(dto.getPassword()));
    }

    Operator updated = operatorRepository.save(operator);
    return mapToDTO(updated);
  }

  public void deleteOperator(UUID id) {
    if (!operatorRepository.existsById(id)) {
      throw new RuntimeException("Operador no encontrado");
    }

    // A Turn must never be destroyed as a side effect of removing the operator
    // who attended it. Same guard as StablishmentService.delete.
    if (turnRepository.existsByOperatorUuid(id)) {
      throw new RuntimeException(
          "No se puede eliminar el operador porque tiene turnos atendidos asociados. Reasigne los turnos antes de eliminarlo.");
    }
    operatorRepository.deleteById(id);
  }

  public Page<OperatorDTO> getAll(Pageable pageable) {
    return operatorRepository.findAll(pageable).map(this::mapToDTO);
  }

  public OperatorDTO getOperatorById(UUID id) {
    Operator operator = operatorRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Operador no encontrado"));
    return mapToDTO(operator);
  }

  public OperatorDTO assignToStablishment(UUID operatorId, Long stablishmentId) {
      Operator operator = operatorRepository.findById(operatorId)
          .orElseThrow(() -> new RuntimeException("Operador no encontrado"));
      
      com.devluis.entity.Stablishment stablishment = stablishmentRepository.findById(stablishmentId)
          .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));
          
      operator.setStablishment(stablishment);
      return mapToDTO(operatorRepository.save(operator));
  }

  public void updatePassword(UUID id, String newPassword) {
    Operator operator = operatorRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Operador no encontrado"));
    operator.setPassword(passwordEncoder.encode(newPassword));
    operatorRepository.save(operator);
  }

  private Operator mapToEntity(OperatorDTO dto) {
    return Operator.builder()
        .email(dto.getEmail())
        .firstName(dto.getFirstName())
        .lastName(dto.getLastName())
        .role(dto.getRole())
        .build();
  }

  private OperatorDTO mapToDTO(Operator operator) {
    com.devluis.dto.StablishmentDTO estDTO = null;
    if (operator.getStablishment() != null) {
      estDTO = com.devluis.dto.StablishmentDTO.builder()
          .id(operator.getStablishment().getId())
          .name(operator.getStablishment().getName())
          .address(operator.getStablishment().getAddress())
          .build();
    }

    return OperatorDTO.builder()
        .uuid(operator.getUuid())
        .email(operator.getEmail())
        .firstName(operator.getFirstName())
        .lastName(operator.getLastName())
        .role(operator.getRole())
        .stablishment(estDTO)
        .build();
  }
}
