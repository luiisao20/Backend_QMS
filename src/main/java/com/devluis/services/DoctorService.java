package com.devluis.services;

import com.devluis.dto.DoctorDTO;
import com.devluis.dto.ServicioDTO;
import com.devluis.dto.StablishmentDTO;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import java.util.UUID;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DoctorService implements UserDetailsService {
  private final DoctorRepository doctorRepository;
  private final PasswordEncoder passwordEncoder;
  private final com.devluis.repository.StablishmentRepository stablishmentRepository;
  private final com.devluis.repository.ServiceRepository serviceRepository;
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
    // Same guard as StablishmentService.delete / ServicioService.delete: a
    // turn-less Schedule is disposable, a Turn never is.
    if (turnRepository.existsByScheduleDoctorUuid(id)) {
      throw new RuntimeException(
          "No se puede eliminar el doctor porque tiene turnos reservados asociados a sus horarios. Cancele o reasigne los turnos antes de eliminarlo.");
    }
    doctorRepository.deleteById(id);
  }

  public Page<DoctorDTO> getAll(String name, String ci, Pageable pageable) {
    if ((name != null && !name.trim().isEmpty()) || (ci != null && !ci.trim().isEmpty())) {
      String cleanName = (name != null && !name.trim().isEmpty()) ? name.trim() : null;
      String cleanCi = (ci != null && !ci.trim().isEmpty()) ? ci.trim() : null;
      return doctorRepository.findByFilters(cleanName, cleanCi, pageable).map(this::mapToDTO);
    }
    return doctorRepository.findAll(pageable).map(this::mapToDTO);
  }

  public Page<DoctorDTO> getAll(Pageable pageable) {
    return getAll(null, null, pageable);
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

  public void updatePassword(UUID id, String newPassword) {
    Doctor doctor = doctorRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Doctor no encontrado"));
    doctor.setPassword(passwordEncoder.encode(newPassword));
    doctorRepository.save(doctor);
  }

  public DoctorDTO assignToService(UUID id, Long serviceId) {
      Doctor doctor = doctorRepository.findById(id)
          .orElseThrow(() -> new RuntimeException("Doctor no encontrado"));
      
      com.devluis.entity.Servicio servicio = serviceRepository.findById(serviceId)
          .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));
          
      if (doctor.getServices() == null) {
          doctor.setServices(new java.util.ArrayList<>());
      }
      if (!doctor.getServices().contains(servicio)) {
          doctor.getServices().add(servicio);
      }
      return mapToDTO(doctorRepository.save(doctor));
  }

  public DoctorDTO revokeStablishment(UUID doctorId, Long stablishmentId) {
      Doctor doctor = doctorRepository.findById(doctorId)
          .orElseThrow(() -> new RuntimeException("Doctor no encontrado"));
      
      com.devluis.entity.Stablishment stablishment = stablishmentRepository.findById(stablishmentId)
          .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));
          
      if (doctor.getStablishments() != null && doctor.getStablishments().contains(stablishment)) {
          doctor.getStablishments().remove(stablishment);
          doctorRepository.save(doctor);
      }
      return mapToDTO(doctor);
  }

  public DoctorDTO revokeService(UUID doctorId, Long serviceId) {
      Doctor doctor = doctorRepository.findById(doctorId)
          .orElseThrow(() -> new RuntimeException("Doctor no encontrado"));
      
      com.devluis.entity.Servicio servicio = serviceRepository.findById(serviceId)
          .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));
          
      if (doctor.getServices() != null && doctor.getServices().contains(servicio)) {
          doctor.getServices().remove(servicio);
          doctorRepository.save(doctor);
      }
      return mapToDTO(doctor);
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
        .stablishments(doctor.getStablishments() != null ? doctor.getStablishments().stream()
            .map(s -> StablishmentDTO.builder().id(s.getId()).name(s.getName()).address(s.getAddress()).build())
            .toList() : null)
        .services(doctor.getServices() != null ? doctor.getServices().stream()
            .map(s -> ServicioDTO.builder().id(s.getId()).name(s.getName()).price(s.getPrice()).discount(s.getDiscount()).build())
            .toList() : null)
        .build();
  }
}
