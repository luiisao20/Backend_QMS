package com.devluis.services;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.devluis.dto.PatientDTO;
import com.devluis.entity.Patient;
import com.devluis.repository.PatientRepository;
import com.devluis.types.Role;

import lombok.Data;

import java.util.Optional;
import java.util.UUID;

@Service
@Data
public class PatientService implements UserDetailsService {
  private final PasswordEncoder passwordEncoder;
  private final PatientRepository patientRepository;

  public Authentication loginEmail(String email, String password) {
    try {
      UserDetails userDetails = loadUserByUsername(email);
      if (passwordEncoder.matches(password, userDetails.getPassword())) {
        return new UsernamePasswordAuthenticationToken(userDetails, userDetails.getAuthorities());
      }
      throw new BadCredentialsException("Error de autenticación");
    } catch (Exception e) {
      throw e;
    }
  }

  public Authentication loginCI(String ci, String password) {
    try {
      UserDetails userDetails = loadUserByUsername(ci);
      if (passwordEncoder.matches(password, userDetails.getPassword())) {
        return new UsernamePasswordAuthenticationToken(userDetails, userDetails.getAuthorities());
      }
      throw new BadCredentialsException("Error de autenticación");
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public UserDetails loadUserByUsername(String email) {
    Patient patient = patientRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("User not found"));

    return User.builder()
        .username(patient.getUuid().toString())
        .password(patient.getPassword())
        .authorities(patient.getRole().name())
        .build();
  }

  public UserDetails loadUserByCi(String ci) {
    Patient patient = patientRepository.findByCi(ci)
        .orElseThrow(() -> new RuntimeException("User not found"));

    return User.builder()
        .username(patient.getUuid().toString())
        .password(patient.getPassword())
        .authorities(patient.getRole().name())
        .build();
  }

  public PatientDTO register(PatientDTO patientDTO) {
    if (patientRepository.findByEmail(patientDTO.getEmail()).isPresent()) {
      throw new RuntimeException("Email ya registrado");
    }

    Patient patient = mapToEntity(patientDTO);
    patient.setPassword(passwordEncoder.encode(patientDTO.getPassword()));
    patient.setRole(Role.ROLE_PATIENT);

    Patient savedPatient = patientRepository.save(patient);
    return mapToDTO(savedPatient);
  }

  public Optional<Patient> findByEmail(String email) {
    return patientRepository.findByEmail(email);
  }

  public Optional<Patient> findByCi(String ci) {
    return patientRepository.findByCi(ci);
  }

  public PatientDTO updatePatient(UUID id, PatientDTO patientDTO) {
    Patient patient = patientRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Paciente no encontrado"));

    patient.setFirstName(patientDTO.getFirstName());
    patient.setLastName(patientDTO.getLastName());
    patient.setCi(patientDTO.getCi());
    patient.setBirthday(patientDTO.getBirthday());
    patient.setGender(patientDTO.getGender());
    patient.setAddress(patientDTO.getAddress());
    patient.setPhone(patientDTO.getPhone());
    patient.setEmergencyContactPhone(patientDTO.getEmergencyContactPhone());
    patient.setEmergencyContactName(patientDTO.getEmergencyContactName());

    if (!patient.getEmail().equals(patientDTO.getEmail())) {
      if (patientRepository.findByEmail(patientDTO.getEmail()).isPresent()) {
        throw new RuntimeException("Email ya registrado");
      }
      patient.setEmail(patientDTO.getEmail());
    }

    if (patientDTO.getPassword() != null && !patientDTO.getPassword().trim().isEmpty()) {
      patient.setPassword(passwordEncoder.encode(patientDTO.getPassword()));
    }

    Patient updatedPatient = patientRepository.save(patient);
    return mapToDTO(updatedPatient);
  }

  public void deletePatient(UUID id) {
    if (!patientRepository.existsById(id)) {
      throw new RuntimeException("Paciente no encontrado");
    }
    patientRepository.deleteById(id);
  }

  public PatientDTO getPatientById(UUID id) {
    Patient patient = patientRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Paciente no encontrado"));
    return mapToDTO(patient);
  }

  private Patient mapToEntity(PatientDTO dto) {
    return Patient.builder()
        .email(dto.getEmail())
        .firstName(dto.getFirstName())
        .lastName(dto.getLastName())
        .ci(dto.getCi())
        .birthday(dto.getBirthday())
        .gender(dto.getGender())
        .address(dto.getAddress())
        .phone(dto.getPhone())
        .emergencyContactPhone(dto.getEmergencyContactPhone())
        .emergencyContactName(dto.getEmergencyContactName())
        .build();
  }

  private PatientDTO mapToDTO(Patient patient) {
    return PatientDTO.builder()
        .uuid(patient.getUuid())
        .email(patient.getEmail())
        .firstName(patient.getFirstName())
        .lastName(patient.getLastName())
        .ci(patient.getCi())
        .birthday(patient.getBirthday())
        .gender(patient.getGender())
        .address(patient.getAddress())
        .phone(patient.getPhone())
        .emergencyContactPhone(patient.getEmergencyContactPhone())
        .emergencyContactName(patient.getEmergencyContactName())
        .build();
  }
}
