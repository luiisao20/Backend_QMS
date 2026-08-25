package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.security.core.context.SecurityContextHolder;

import com.devluis.jwt.JwtConstants;
import com.devluis.repository.DoctorRepository;
import com.devluis.repository.OperatorRepository;
import com.devluis.repository.PatientRepository;
import com.devluis.types.AuthResult;
import com.devluis.types.InitRegistrationBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private MailService mailService;
  @Mock
  private DoctorService doctorService;
  @Mock
  private PatientService patientService;
  @Mock
  private OperatorService operatorService;
  @Mock
  private OtpService otpService;
  @Mock
  private PatientRepository patientRepository;
  @Mock
  private DoctorRepository doctorRepository;
  @Mock
  private OperatorRepository operatorRepository;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    // JwtProvider.generateFlashToken reads this static field, which Spring
    // only populates via JwtConstants' @PostConstruct. There is no Spring
    // context here, so it must be set by hand or every JWT-issuing path NPEs
    // on a null secret. 32+ chars because jjwt's Keys.hmacShaKeyFor rejects
    // shorter HMAC-SHA keys as too weak.
    JwtConstants.SECRET_KEY_STATIC = "unit-test-secret-key-not-for-real-use!!";

    authService = new AuthService(mailService, doctorService, patientService, operatorService,
        otpService, patientRepository, doctorRepository, operatorRepository);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void initRegistration_stillReturnsOk_whenNotificationEmailFails() {
    InitRegistrationBody body = new InitRegistrationBody();
    body.setEmail("nuevo@example.com");
    body.setCi("1234567890");

    when(patientRepository.findByCi("1234567890")).thenReturn(Optional.empty());
    when(patientRepository.findByEmail("nuevo@example.com")).thenReturn(Optional.empty());
    when(doctorRepository.findByEmail("nuevo@example.com")).thenReturn(Optional.empty());
    when(operatorRepository.findByEmail("nuevo@example.com")).thenReturn(Optional.empty());
    when(otpService.generateOtp()).thenReturn("123456");
    doThrow(new MailSendException("SMTP no disponible"))
        .when(mailService).sendTestEmail(eq("nuevo@example.com"), any(), any());

    AuthResult<AuthService.InitRegistrationResult> result = authService.initRegistration(
        mock(HttpServletRequest.class), mock(HttpServletResponse.class), body);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData().getJwtToken()).isNotBlank();
    // The OTP must survive a mail outage: it was already saved before the
    // (now-guarded) send call, so recovery/registration can still proceed
    // through other channels.
    verify(otpService).saveOtp("nuevo@example.com", "123456");
  }
}
