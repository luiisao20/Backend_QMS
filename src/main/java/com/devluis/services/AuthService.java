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
    if (patientRepository.findByCi(body.getCi()).isPresent()) {
      return AuthResult.error("Ya existe un paciente registrado con esta cédula", HttpStatus.BAD_REQUEST);
    }

    if (patientRepository.findByEmail(body.getEmail()).isPresent() ||
        doctorRepository.findByEmail(body.getEmail()).isPresent() ||
        operatorRepository.findByEmail(body.getEmail()).isPresent()) {
      return AuthResult.error("Ya existe un usuario registrado con este correo electrónico", HttpStatus.BAD_REQUEST);
    }

    String email = body.getEmail().trim().toLowerCase();
    String otp = otpService.generateOtp();
    otpService.saveOtp(email, otp);

    Authentication auth = new UsernamePasswordAuthenticationToken(email, null,
        List.of(new SimpleGrantedAuthority("ROLE_OTP_PENDING")));
    String jwt = JwtProvider.generateFlashToken(auth);
    SecurityContextHolder.getContext().setAuthentication(auth);

    // The OTP is already saved at this point. If the mail server is down, the
    // registration flow must not blow up on an already-stored OTP — log and
    // continue, same pattern as TurnService.sendTurnEmail.
    try {
      mailService.sendOtpEmail(email, "Completa tu registro - Código OTP", otp, "registro en la plataforma");
    } catch (Exception e) {
      System.err.println("Error al enviar correo de OTP de registro: " + e.getMessage());
    }

    return AuthResult.ok(InitRegistrationResult.builder().jwtToken(jwt).build());
  }

  public AuthResult<String> verifyRegistrationOtp(String email, String inputOtp) {
    if (email == null || email.isBlank()) {
      return AuthResult.error("No hay una sesión de verificación activa", HttpStatus.UNAUTHORIZED);
    }

    if (otpService.isBlocked(email)) {
      return AuthResult.error("Has superado el límite de intentos permitidos", HttpStatus.BAD_REQUEST);
    }

    if (!otpService.validate(email, inputOtp)) {
      return AuthResult.error("Código OTP incorrecto o expirado", HttpStatus.BAD_REQUEST);
    }

    otpService.deleteOtp(email);

    Authentication auth = new UsernamePasswordAuthenticationToken(email, null,
        List.of(new SimpleGrantedAuthority("ROLE_PENDING_REGISTRATION")));
    String jwt = JwtProvider.generateFlashToken(auth);
    SecurityContextHolder.getContext().setAuthentication(auth);

    return AuthResult.ok(jwt);
  }

  public AuthResult<RegistrationResult> completeRegistration(String emailAuth, PatientDTO patient) {
    if (emailAuth == null || patient.getEmail() == null || !emailAuth.equalsIgnoreCase(patient.getEmail().trim())) {
      return AuthResult.error("El correo del formulario no coincide con el correo verificado en la sesión",
          HttpStatus.BAD_REQUEST);
    }

    if (patientRepository.findByCi(patient.getCi()).isPresent()) {
      return AuthResult.error("Ya existe un paciente registrado con esta cédula", HttpStatus.BAD_REQUEST);
    }

    if (patientRepository.findByEmail(emailAuth).isPresent()) {
      return AuthResult.error("Ya existe un paciente registrado con este correo electrónico", HttpStatus.BAD_REQUEST);
    }

    patient.setEmail(emailAuth);
    Patient patientRegistered = patientService.register(patient);

    Authentication auth = patientService.loginEmail(patientRegistered.getEmail(), patient.getPassword());
    String jwt = JwtProvider.generateToken(auth);
    SecurityContextHolder.getContext().setAuthentication(auth);

    mailService.sendRegistrationSuccessEmail(emailAuth, patientRegistered.getFirstName(), patientRegistered.getLastName());

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
        .ci(user.getCi())
        .message(message)
        .build();
  }

  private AuthResponse buildAuthResponse(com.devluis.entity.Doctor user, String message) {
    return AuthResponse.builder()
        .email(user.getEmail())
        .role(user.getRole().name())
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .ci(user.getCi())
        .message(message)
        .build();
  }

  private AuthResponse buildAuthResponse(com.devluis.entity.Operator user, String message) {
    return AuthResponse.builder()
        .email(user.getEmail())
        .role(user.getRole().name())
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .stablishmentName(user.getStablishment() != null ? user.getStablishment().getName() : null)
        .message(message)
        .build();
  }

  public AuthResult<String> initPasswordRecovery(String email) {
    boolean exists = patientRepository.findByEmail(email).isPresent() ||
        doctorRepository.findByEmail(email).isPresent() ||
        operatorRepository.findByEmail(email).isPresent();
    if (!exists) {
      return AuthResult.error("No existe un usuario con ese correo", HttpStatus.NOT_FOUND);
    }

    String otp = otpService.generateOtp();
    otpService.saveOtp(email, otp);

    Authentication auth = new UsernamePasswordAuthenticationToken(email, null,
        List.of(new SimpleGrantedAuthority("ROLE_OTP_PENDING")));
    String jwt = JwtProvider.generateFlashToken(auth);

    mailService.sendOtpEmail(email, "Recuperación de contraseña", otp, "recuperación de contraseña");

    return AuthResult.ok(jwt);
  }

  public AuthResult<String> verifyRecoveryOtp(String email, String inputOtp) {
    if (otpService.isBlocked(email)) {
      return AuthResult.error("Has superado el límite de intentos", HttpStatus.BAD_REQUEST);
    }
    if (!otpService.validate(email, inputOtp)) {
      return AuthResult.error("Código OTP incorrecto o expirado", HttpStatus.BAD_REQUEST);
    }
    otpService.deleteOtp(email);

    Authentication auth = new UsernamePasswordAuthenticationToken(email, null,
        List.of(new SimpleGrantedAuthority("ROLE_CHANGE_PASSWORD")));
    String jwt = JwtProvider.generateFlashToken(auth);

    return AuthResult.ok(jwt);
  }

  public AuthResult<String> changePassword(String email, String newPassword, String repeatedPassword) {
    if (!newPassword.equals(repeatedPassword)) {
      return AuthResult.error("Las contraseñas no coinciden", HttpStatus.BAD_REQUEST);
    }

    String encodedPassword = patientService.getPasswordEncoder().encode(newPassword);

    var patientOpt = patientRepository.findByEmail(email);
    if (patientOpt.isPresent()) {
      var patient = patientOpt.get();
      patient.setPassword(encodedPassword);
      patientRepository.save(patient);
      return AuthResult.ok("Contraseña actualizada exitosamente");
    }

    var doctorOpt = doctorRepository.findByEmail(email);
    if (doctorOpt.isPresent()) {
      var doctor = doctorOpt.get();
      doctor.setPassword(encodedPassword);
      doctorRepository.save(doctor);
      return AuthResult.ok("Contraseña actualizada exitosamente");
    }

    var operatorOpt = operatorRepository.findByEmail(email);
    if (operatorOpt.isPresent()) {
      var operator = operatorOpt.get();
      operator.setPassword(encodedPassword);
      operatorRepository.save(operator);
      return AuthResult.ok("Contraseña actualizada exitosamente");
    }

    return AuthResult.error("Usuario no encontrado", HttpStatus.NOT_FOUND);
  }
}
