package com.devluis.services;

import com.devluis.dto.DoctorDTO;
import com.devluis.entity.Doctor;
import com.devluis.repository.DoctorRepository;
import com.devluis.types.Role;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import java.util.UUID;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DoctorService implements org.springframework.security.core.userdetails.UserDetailsService {
  private final DoctorRepository doctorRepository;
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

  public Authentication loginCI(String ci, String password) {
    try {
      UserDetails userDetails = loadUserByCi(ci);

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
    Doctor doctor = doctorRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    return User.builder()
        .username(doctor.getUuid().toString())
        .password(doctor.getPassword())
        .authorities(doctor.getRole().name())
        .build();
  }

  public UserDetails loadUserByCi(String ci) {
    Doctor doctor = doctorRepository.findByCi(ci)
        .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    return User.builder()
        .username(doctor.getUuid().toString())
        .password(doctor.getPassword())
        .authorities(doctor.getRole().name())
        .build();
  }

  public Optional<Doctor> findByEmail(String email) {
    return doctorRepository.findByEmail(email);
  }

  // We will need findByCi in DoctorRepository!
  public Optional<Doctor> findByCi(String ci) {
    return doctorRepository.findByCi(ci);
  }

  public DoctorDTO register(DoctorDTO dto) {
    if (doctorRepository.findByEmail(dto.getEmail()).isPresent()) {
      throw new RuntimeException("Email ya registrado");
    }

    Doctor doctor = mapToEntity(dto);
    doctor.setEmail(dto.getEmail().toLowerCase());
    doctor.setPassword(passwordEncoder.encode(dto.getPassword()));
    doctor.setRole(Role.ROLE_DOCTOR);

    Doctor saved = doctorRepository.save(doctor);
    return mapToDTO(saved);
  }

  public DoctorDTO updateDoctor(UUID id, DoctorDTO dto) {
    Doctor doctor = doctorRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Doctor no encontrado"));

    doctor.setFirstName(dto.getFirstName());
    doctor.setLastName(dto.getLastName());
    doctor.setSpeciality(dto.getSpeciality());
    doctor.setGender(dto.getGender());
    doctor.setCi(dto.getCi());

    if (!doctor.getEmail().equals(dto.getEmail())) {
      if (doctorRepository.findByEmail(dto.getEmail()).isPresent()) {
        throw new RuntimeException("Email ya registrado");
      }
      doctor.setEmail(dto.getEmail());
    }

    if (dto.getPassword() != null && !dto.getPassword().trim().isEmpty()) {
      doctor.setPassword(passwordEncoder.encode(dto.getPassword()));
    }

    Doctor updated = doctorRepository.save(doctor);
    return mapToDTO(updated);
  }

  public void deleteDoctor(UUID id) {
    if (!doctorRepository.existsById(id)) {
      throw new RuntimeException("Doctor no encontrado");
    }
    doctorRepository.deleteById(id);
  }

  public Page<DoctorDTO> getAll(Pageable pageable) {
      return doctorRepository.findAll(pageable).map(this::mapToDTO);
  }

  public DoctorDTO getDoctorById(UUID id) {
    Doctor doctor = doctorRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Doctor no encontrado"));
    return mapToDTO(doctor);
  }

  public DoctorDTO assignToStablishment(UUID doctorId, Long stablishmentId) {
      Doctor doctor = doctorRepository.findById(doctorId)
          .orElseThrow(() -> new RuntimeException("Doctor no encontrado"));
      
      com.devluis.entity.Stablishment stablishment = stablishmentRepository.findById(stablishmentId)
          .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));
          
      if (doctor.getStablishments() == null) {
          doctor.setStablishments(new java.util.ArrayList<>());
      }
      if (!doctor.getStablishments().contains(stablishment)) {
          doctor.getStablishments().add(stablishment);
      }
      return mapToDTO(doctorRepository.save(doctor));
  }

  private Doctor mapToEntity(DoctorDTO dto) {
    return Doctor.builder()
        .email(dto.getEmail())
        .firstName(dto.getFirstName())
        .lastName(dto.getLastName())
        .speciality(dto.getSpeciality())
        .gender(dto.getGender())
        .ci(dto.getCi())
        .build();
  }

  private DoctorDTO mapToDTO(Doctor doctor) {
    return DoctorDTO.builder()
        .uuid(doctor.getUuid())
        .email(doctor.getEmail())
        .firstName(doctor.getFirstName())
        .lastName(doctor.getLastName())
        .speciality(doctor.getSpeciality())
        .gender(doctor.getGender())
        .ci(doctor.getCi())
        .build();
  }
}
