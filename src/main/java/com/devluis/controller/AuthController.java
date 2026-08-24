package com.devluis.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.devluis.services.AuthService;
import com.devluis.services.DoctorService;
import com.devluis.services.OperatorService;
import com.devluis.dto.PatientDTO;
import com.devluis.dto.DoctorDTO;
import com.devluis.dto.OperatorDTO;
import com.devluis.types.ChangePasswordBody;
import com.devluis.types.InitRegistrationBody;
import com.devluis.types.LoginDoctorBody;
import com.devluis.types.LoginOperatorBody;
import com.devluis.types.LoginPatientBody;
import com.devluis.types.RecoverPasswordInitBody;
import com.devluis.types.VerifyOtpBody;
import com.devluis.utils.Helper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
  private final AuthService authService;
  private final DoctorService doctorService;
  private final OperatorService operatorService;

  @PostMapping("/login-patient")
  public ResponseEntity<?> loginPatient(
      @Valid @RequestBody LoginPatientBody entity,
      HttpServletRequest req,
      HttpServletResponse res) {
    var result = authService.loginPatient(res, req, entity);

    if (!result.isSuccess()) {
      return Helper.getResponseMessage(result.getMessage(), result.getStatus());
    }
    Helper.addJwtCookie(res, result.getData().getJwtToken(), 86400);
    return ResponseEntity.ok(result.getData().getAuthResponse());
  }

  @PostMapping("/login-doctor")
  public ResponseEntity<?> loginDoctor(
      @Valid @RequestBody LoginDoctorBody entity,
      HttpServletRequest req,
      HttpServletResponse res) {
    var result = authService.loginDoctor(res, req, entity);

    if (!result.isSuccess()) {
      return Helper.getResponseMessage(result.getMessage(), result.getStatus());
    }
    Helper.addJwtCookie(res, result.getData().getJwtToken(), 86400);
    return ResponseEntity.ok(result.getData().getAuthResponse());
  }

  @PostMapping("/login-operator")
  public ResponseEntity<?> loginOperator(
      @Valid @RequestBody LoginOperatorBody entity,
      HttpServletRequest req,
      HttpServletResponse res) {
    var result = authService.loginOperator(res, req, entity);

    if (!result.isSuccess()) {
      return Helper.getResponseMessage(result.getMessage(), result.getStatus());
    }
    Helper.addJwtCookie(res, result.getData().getJwtToken(), 86400);
    return ResponseEntity.ok(result.getData().getAuthResponse());
  }

  @PostMapping("/mobile/login-patient")
  public ResponseEntity<?> loginPatientMobile(
      @Valid @RequestBody LoginPatientBody entity,
      HttpServletRequest req,
      HttpServletResponse res) {
    var result = authService.loginPatient(res, req, entity);

    if (!result.isSuccess()) {
      return Helper.getResponseMessage(result.getMessage(), result.getStatus());
    }
    return ResponseEntity.ok()
        .header("Authorization", "Bearer " + result.getData().getJwtToken())
        .body(result.getData().getAuthResponse());
  }

  @PostMapping("/init-registration-patient")
  public ResponseEntity<?> initRegistration(
      @Valid @RequestBody InitRegistrationBody body,
      HttpServletRequest req,
      HttpServletResponse res) {
    var result = authService.initRegistration(req, res, body);

    if (!result.isSuccess()) {
      return Helper.getResponseMessage(result.getMessage(), result.getStatus());
    }

    Helper.addJwtCookie(res, result.getData().getJwtToken(), 300);

    return ResponseEntity.ok(Map.of(
        "Message", "Código OTP enviado al correo",
        "email", body.getEmail()));
  }

  @PostMapping("/verify-registration-otp")
  public ResponseEntity<?> verifyRegistrationOtp(
      @Valid @RequestBody VerifyOtpBody body,
      HttpServletRequest req,
      HttpServletResponse res,
      Authentication auth) {
    if (auth == null || auth.getName() == null) {
      return Helper.getResponseMessage("Sesión de verificación inválida o inexistente", HttpStatus.UNAUTHORIZED);
    }
    String email = auth.getName();
    var result = authService.verifyRegistrationOtp(email, body.getOtp());
    if (!result.isSuccess()) {
      return Helper.getResponseMessage(result.getMessage(), result.getStatus());
    }

    Helper.addJwtCookie(res, result.getData(), 600);

    return ResponseEntity.ok(Map.of(
        "Message", "Código OTP verificado correctamente",
        "email", email));
  } 

  @PostMapping("/register-patient")
  public ResponseEntity<?> registerPatient(
      @Valid @RequestBody PatientDTO dto,
      HttpServletRequest req,
      HttpServletResponse res,
      Authentication auth) {
    if (auth == null || auth.getName() == null) {
      return Helper.getResponseMessage("No autorizado para completar el registro",
          HttpStatus.UNAUTHORIZED);
    }
    String emailAuth = auth.getName();
    var result = authService.completeRegistration(emailAuth, dto);

    if (!result.isSuccess()) {
      return Helper.getResponseMessage(result.getMessage(), result.getStatus());
    }

    Helper.addJwtCookie(res, result.getData().getJwtToken(), 86400);

    return ResponseEntity.ok(result.getData().getAuthResponse());
  }

  @PostMapping("/register-doctor")
  public ResponseEntity<?> registerDoctor(@Valid @RequestBody DoctorDTO dto) {
    try {
      DoctorDTO registered = doctorService.register(dto);
      return ResponseEntity.ok(registered);
    } catch (RuntimeException e) {
      return Helper.getResponseMessage(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @PostMapping("/register-operator")
  public ResponseEntity<?> registerOperator(@Valid @RequestBody OperatorDTO dto) {
    try {
      OperatorDTO registered = operatorService.register(dto);
      return ResponseEntity.ok(registered);
    } catch (RuntimeException e) {
      return Helper.getResponseMessage(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @GetMapping("/me")
  public ResponseEntity<?> validateSession(Authentication auth) {
    var result = authService.validateSession(auth);
    if (!result.isSuccess()) {
      return Helper.getResponseMessage(result.getMessage(), result.getStatus());
    }
    return ResponseEntity.ok(result.getData());
  }

  @PostMapping("/recover-password/init")
  public ResponseEntity<?> initPasswordRecovery(
      @Valid @RequestBody RecoverPasswordInitBody body,
      HttpServletResponse res) {
    var result = authService.initPasswordRecovery(body.getEmail());
    if (!result.isSuccess()) {
      return Helper.getResponseMessage(result.getMessage(), result.getStatus());
    }
    // Token with ROLE_OTP_PENDING for 5 minutes
    Helper.addJwtCookie(res, result.getData(), 300);
    return ResponseEntity.ok(Map.of("Message", "Se ha enviado un código OTP a tu correo"));
  }

  @PostMapping("/recover-password/verify-otp")
  public ResponseEntity<?> verifyRecoveryOtp(
      @Valid @RequestBody VerifyOtpBody body,
      HttpServletRequest req,
      HttpServletResponse res,
      Authentication auth) {
    String email = auth.getName();
    var result = authService.verifyRecoveryOtp(email, body.getOtp());
    if (!result.isSuccess()) {
      return Helper.getResponseMessage(result.getMessage(), result.getStatus());
    }
    // Token with ROLE_CHANGE_PASSWORD for 10 minutes
    Helper.addJwtCookie(res, result.getData(), 600);
    return ResponseEntity.ok(Map.of("Message", "Código verificado correctamente"));
  }

  @PostMapping("/recover-password/change")
  public ResponseEntity<?> changePassword(
      @Valid @RequestBody ChangePasswordBody body,
      HttpServletRequest req,
      HttpServletResponse res,
      Authentication auth) {
    String email = auth.getName();
    var result = authService.changePassword(email, body.getPassword(), body.getRepeatedPassword());
    if (!result.isSuccess()) {
      return Helper.getResponseMessage(result.getMessage(), result.getStatus());
    }

    // Clear the cookie once the password is changed
    Helper.deleteJwtCookie(res);

    return ResponseEntity.ok(Map.of("Message", result.getData()));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletRequest req, HttpServletResponse res) {
    SecurityContextHolder.clearContext();
    Helper.deleteJwtCookie(res);
    return ResponseEntity.ok(Map.of("message", "Sesión cerrada correctamente"));
  }
}
