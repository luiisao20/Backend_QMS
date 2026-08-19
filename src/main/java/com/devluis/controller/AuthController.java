package com.devluis.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.devluis.services.AuthService;
import com.devluis.services.DoctorService;
import com.devluis.services.OperatorService;
import com.devluis.dto.PatientDTO;
import com.devluis.dto.DoctorDTO;
import com.devluis.dto.OperatorDTO;
import com.devluis.types.InitRegistrationBody;
import com.devluis.types.LoginDoctorBody;
import com.devluis.types.LoginOperatorBody;
import com.devluis.types.LoginPatientBody;
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

    return ResponseEntity.ok(Map.of("Message", "Código Otp enviado al correo"));
  }

  @PostMapping("/register-patient")
  public ResponseEntity<?> registerPatient(
      @Valid @RequestBody PatientDTO dto,
      HttpServletRequest req,
      HttpServletResponse res,
      Authentication auth) {
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
      return Helper.getResponseMessage(e.getMessage(), org.springframework.http.HttpStatus.BAD_REQUEST);
    }
  }

  @PostMapping("/register-operator")
  public ResponseEntity<?> registerOperator(@Valid @RequestBody OperatorDTO dto) {
    try {
      OperatorDTO registered = operatorService.register(dto);
      return ResponseEntity.ok(registered);
    } catch (RuntimeException e) {
      return Helper.getResponseMessage(e.getMessage(), org.springframework.http.HttpStatus.BAD_REQUEST);
    }
  }
}
