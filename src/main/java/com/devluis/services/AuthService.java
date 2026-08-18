package com.devluis.services;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.devluis.entity.Operator;
import com.devluis.entity.Patient;
import com.devluis.jwt.JwtProvider;
import com.devluis.types.AuthResponse;
import com.devluis.types.AuthResult;
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
  private final DoctorService doctorService;
  private final PatientService patientService;
  private final OperatorService operatorService;

  @Data
  @Builder
  public static class LoginResult {
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
