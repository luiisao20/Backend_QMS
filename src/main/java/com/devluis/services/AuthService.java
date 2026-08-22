package com.devluis.services;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.devluis.dto.PatientDTO;
import com.devluis.entity.Operator;
import com.devluis.entity.Patient;
import com.devluis.jwt.JwtProvider;
import com.devluis.types.AuthResponse;
import com.devluis.types.AuthResult;
import com.devluis.types.InitRegistrationBody;
import com.devluis.types.LoginDoctorBody;
import com.devluis.types.LoginOperatorBody;
import com.devluis.types.LoginPatientBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Builder;
import lombok.Data;

@Service
@Data
public class AuthService {
  private final MailService mailService;
  private final DoctorService doctorService;
  private final PatientService patientService;
  private final OperatorService operatorService;
  private final OtpService otpService;
  private final com.devluis.repository.PatientRepository patientRepository;
  private final com.devluis.repository.DoctorRepository doctorRepository;
  private final com.devluis.repository.OperatorRepository operatorRepository;

  @Data
  @Builder
  public static class LoginResult {
    private AuthResponse authResponse;
    private String jwtToken;
  }

  @Data
  @Builder
  public static class InitRegistrationResult {
    private String jwtToken;
  }

  @Data
  @Builder
  public static class RegistrationResult {
    private AuthResponse authResponse;
    private String jwtToken;
  }

  public AuthResult<LoginResult> loginPatient(HttpServletResponse res, HttpServletRequest req, LoginPatientBody body) {
    try {
      Authentication auth = null;
      Patient patient = null;
      if (body.getCi() != null) {
        auth = patientService.loginCI(body.getCi(), body.getPassword());
        patient = patientService.findByCi(body.getCi())
            .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
      } else if (body.getEmail() != null) {
        auth = patientService.loginEmail(body.getEmail(), body.getPassword());
        patient = patientService.findByEmail(body.getEmail())
            .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
      }
      SecurityContextHolder.getContext().setAuthentication(auth);
      String jwt = JwtProvider.generateToken(auth);

      return AuthResult.ok(LoginResult.builder()
          .authResponse(buildAuthResponse(patient, "Login Exitoso"))
          .jwtToken(jwt)
          .build());
    } catch (RuntimeException e) {
      return AuthResult.error("Error de autenticación", HttpStatus.NOT_FOUND);
    }
  }

  public AuthResult<LoginResult> loginDoctor(HttpServletResponse res, HttpServletRequest req, LoginDoctorBody body) {
    try {
      Authentication auth = null;
      com.devluis.entity.Doctor doctor = null;
      if (body.getCi() != null) {
        auth = doctorService.loginCI(body.getCi(), body.getPassword());
        doctor = doctorService.findByCi(body.getCi())
            .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
      } else if (body.getEmail() != null) {
        auth = doctorService.loginEmail(body.getEmail(), body.getPassword());
        doctor = doctorService.findByEmail(body.getEmail())
            .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
      }
      SecurityContextHolder.getContext().setAuthentication(auth);
      String jwt = JwtProvider.generateToken(auth);

      return AuthResult.ok(LoginResult.builder()
          .authResponse(buildAuthResponse(doctor, "Login Exitoso"))
          .jwtToken(jwt)
          .build());
    } catch (RuntimeException e) {
      return AuthResult.error("Error de autenticación", HttpStatus.NOT_FOUND);
    }
  }

  public AuthResult<LoginResult> loginOperator(HttpServletResponse res, HttpServletRequest req,
      LoginOperatorBody body) {
    try {
      Authentication auth = operatorService.loginEmail(body.getEmail(), body.getPassword());
      Operator operator = operatorService.findByEmail(body.getEmail())
          .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
      SecurityContextHolder.getContext().setAuthentication(auth);
      String jwt = JwtProvider.generateToken(auth);

      return AuthResult.ok(LoginResult.builder()
          .authResponse(buildAuthResponse(operator, "Login Exitoso"))
          .jwtToken(jwt)
          .build());
    } catch (RuntimeException e) {
      return AuthResult.error("Error de autenticación", HttpStatus.NOT_FOUND);
    }
  }

  public AuthResult<InitRegistrationResult> initRegistration(
      HttpServletRequest req,
      HttpServletResponse res,
      InitRegistrationBody body) {
    if (patientService.findByCi(body.getCi()).isPresent()) {
      return AuthResult.error("El usuario ya se encuentra registrado con esta cédula", HttpStatus.BAD_REQUEST);
    }

    if (patientService.findByEmail(body.getEmail()).isPresent()) {
      return AuthResult.error("El usuario ya se encuentra registrado con este correo", HttpStatus.BAD_REQUEST);
    }

    String otp = otpService.generateOtp();

    Authentication auth = new UsernamePasswordAuthenticationToken(body.getEmail(), null,
        List.of(new SimpleGrantedAuthority("ROLE_OTP_PENDING")));
    String jwt = JwtProvider.generateFlashToken(auth);
    SecurityContextHolder.getContext().setAuthentication(auth);

    mailService.sendTestEmail(body.getEmail(), "Completa tu registro",
        "Se ha generado un código OTP " + otp + " por favor ingrésalo en la plataforma para completar tu registro");

    return AuthResult.ok(InitRegistrationResult.builder().jwtToken(jwt).build());
  }

  public AuthResult<RegistrationResult> completeRegistration(String emailAuth, PatientDTO patient) {
    if (!emailAuth.equals(patient.getEmail())) {
      return AuthResult.error("El email no pertenece al usuario autenticado", HttpStatus.BAD_REQUEST);
    }
    Patient patientRegistered = patientService.register(patient);

    Authentication auth = patientService.loginEmail(patientRegistered.getEmail(), patient.getPassword());
    String jwt = JwtProvider.generateToken(auth);
    SecurityContextHolder.getContext().setAuthentication(auth);

    mailService.sendTestEmail(emailAuth, "Registro exitoso",
        "Te has registrado exitosamente en la plataforma");
    return AuthResult.ok(RegistrationResult.builder()
        .authResponse(buildAuthResponse(patientRegistered, "Registro culminado con éxito"))
        .jwtToken(jwt).build());
  }

  public AuthResult<AuthResponse> validateSession(Authentication auth) {
    try {
      java.util.UUID uuid = java.util.UUID.fromString(auth.getName());
      String role = auth.getAuthorities().stream().findFirst().map(a -> a.getAuthority()).orElse("");

      if (role.equals("ROLE_PATIENT")) {
        Patient patient = patientRepository.findById(uuid)
            .orElseThrow(() -> new BadCredentialsException("Paciente no encontrado"));
        return AuthResult.ok(buildAuthResponse(patient, "Sesión válida"));
      } else if (role.equals("ROLE_DOCTOR")) {
        com.devluis.entity.Doctor doctor = doctorRepository.findById(uuid)
            .orElseThrow(() -> new BadCredentialsException("Doctor no encontrado"));
        return AuthResult.ok(buildAuthResponse(doctor, "Sesión válida"));
      } else if (role.equals("ROLE_EMPLOYEE") || role.equals("ROLE_ADMIN")) {
        Operator operator = operatorRepository.findById(uuid)
            .orElseThrow(() -> new BadCredentialsException("Operador no encontrado"));
        return AuthResult.ok(buildAuthResponse(operator, "Sesión válida"));
      }
      return AuthResult.error("Rol no reconocido", HttpStatus.FORBIDDEN);
    } catch (Exception e) {
      return AuthResult.error("Sesión inválida o expirada", HttpStatus.UNAUTHORIZED);
    }
  }

  private AuthResponse buildAuthResponse(Patient user, String message) {
    return AuthResponse.builder()
        .email(user.getEmail())
        .role(user.getRole().name())
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .message(message)
        .build();
  }

  private AuthResponse buildAuthResponse(com.devluis.entity.Doctor user, String message) {
    return AuthResponse.builder()
        .email(user.getEmail())
        .role(user.getRole().name())
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .message(message)
        .build();
  }

  private AuthResponse buildAuthResponse(com.devluis.entity.Operator user, String message) {
    return AuthResponse.builder()
        .email(user.getEmail())
        .role(user.getRole().name())
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .message(message)
        .build();
  }
}
