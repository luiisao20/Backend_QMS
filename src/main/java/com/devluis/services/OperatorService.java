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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.Data;
import java.util.UUID;
import java.util.Optional;

@Service
@Data
public class OperatorService implements org.springframework.security.core.userdetails.UserDetailsService {
  private final OperatorRepository operatorRepository;
  private final PasswordEncoder passwordEncoder;
  private final com.devluis.repository.StablishmentRepository stablishmentRepository;

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

  private Operator mapToEntity(OperatorDTO dto) {
    return Operator.builder()
        .email(dto.getEmail())
        .firstName(dto.getFirstName())
        .lastName(dto.getLastName())
        .role(dto.getRole())
        .build();
  }

  private OperatorDTO mapToDTO(Operator operator) {
    return OperatorDTO.builder()
        .uuid(operator.getUuid())
        .email(operator.getEmail())
        .firstName(operator.getFirstName())
        .lastName(operator.getLastName())
        .build();
  }
}
