package com.devluis.services;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Service
@Data
@Slf4j
public class MailService {
  private final JavaMailSender javaMailSender;
  private final TemplateEngine templateEngine;

  public void sendTestEmail(String to, String subject, String text) {
    SimpleMailMessage message = new SimpleMailMessage();

    message.setFrom("bravo.luis.1995@gmail.com");
    message.setTo(to);
    message.setSubject(subject);
    message.setText(text);

    javaMailSender.send(message);
  }

  public void sendOtpEmail(String to, String subject, String otp, String action) {
    try {
      MimeMessage message = javaMailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setFrom("bravo.luis.1995@gmail.com");
      helper.setTo(to);
      helper.setSubject(subject);

      Context context = new Context();
      context.setVariable("otp", otp);
      context.setVariable("action", action);

      String htmlBody = templateEngine.process("codigo_otp", context);
      helper.setText(htmlBody, true);

      javaMailSender.send(message);
    } catch (Exception e) {
      log.error("Error enviando email de OTP: ", e);
    }
  }

  public void sendRegistrationSuccessEmail(String to, String firstName, String lastName) {
    try {
      MimeMessage message = javaMailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setFrom("bravo.luis.1995@gmail.com");
      helper.setTo(to);
      helper.setSubject("Registro exitoso - QMS");

      Context context = new Context();
      context.setVariable("firstName", firstName);
      context.setVariable("lastName", lastName);

      String htmlBody = templateEngine.process("registro_exitoso", context);
      helper.setText(htmlBody, true);

      javaMailSender.send(message);
    } catch (Exception e) {
      log.error("Error enviando email de registro exitoso: ", e);
    }
  }

  public void sendTurnCreatedEmail(
      String patientEmail,
      String patientFirstName,
      String patientLastName,
      String patientCi,
      int turnOrder,
      String serviceName,
      String date,
      String hour,
      String stablishmentName,
      String doctorFullName) {

    try {
      MimeMessage message = javaMailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setFrom("bravo.luis.1995@gmail.com");
      helper.setTo(patientEmail);
      helper.setSubject("Confirmación de Turno #" + turnOrder + " - " + serviceName);

      Context context = new Context();
      context.setVariable("patientFirstName", patientFirstName);
      context.setVariable("patientLastName", patientLastName);
      context.setVariable("patientCi", patientCi);
      context.setVariable("turnOrder", turnOrder);
      context.setVariable("stablishmentName", stablishmentName);
      context.setVariable("serviceName", serviceName);
      context.setVariable("doctorFullName", doctorFullName);
      context.setVariable("date", date);
      context.setVariable("hour", hour);

      String htmlBody = templateEngine.process("cita_confirmada", context);
      helper.setText(htmlBody, true);

      javaMailSender.send(message);
    } catch (Exception e) {
      log.error("Error enviando email de confirmación: ", e);
    }
  }

  public void sendTurnRescheduledEmail(
      String patientEmail,
      String patientFirstName,
      String patientLastName,
      int turnOrder,
      String serviceName,
      String date,
      String hour,
      String stablishmentName,
      String doctorFullName) {

    try {
      MimeMessage message = javaMailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setFrom("bravo.luis.1995@gmail.com");
      helper.setTo(patientEmail);
      helper.setSubject("Reasignación de Turno #" + turnOrder + " - " + serviceName);

      Context context = new Context();
      context.setVariable("patientFirstName", patientFirstName);
      context.setVariable("patientLastName", patientLastName);
      context.setVariable("turnOrder", turnOrder);
      context.setVariable("stablishmentName", stablishmentName);
      context.setVariable("serviceName", serviceName);
      context.setVariable("doctorFullName", doctorFullName);
      context.setVariable("date", date);
      context.setVariable("hour", hour);

      String htmlBody = templateEngine.process("cita_reasignada", context);
      helper.setText(htmlBody, true);

      javaMailSender.send(message);
    } catch (Exception e) {
      log.error("Error enviando email de reasignación: ", e);
    }
  }

  public void sendTurnCancelledEmail(
      String patientEmail,
      String patientFirstName,
      String patientLastName,
      int turnOrder,
      String serviceName,
      String date,
      String hour,
      String reason) {

    try {
      MimeMessage message = javaMailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setFrom("bravo.luis.1995@gmail.com");
      helper.setTo(patientEmail);
      helper.setSubject("Cancelación de Turno #" + turnOrder + " - " + serviceName);

      Context context = new Context();
      context.setVariable("patientFirstName", patientFirstName);
      context.setVariable("patientLastName", patientLastName);
      context.setVariable("turnOrder", turnOrder);
      context.setVariable("serviceName", serviceName);
      context.setVariable("date", date);
      context.setVariable("hour", hour);
      context.setVariable("reason", reason);

      String htmlBody = templateEngine.process("cita_cancelada", context);
      helper.setText(htmlBody, true);

      javaMailSender.send(message);
    } catch (Exception e) {
      log.error("Error enviando email de cancelación: ", e);
    }
  }

  public void sendUpcomingTurnReminderEmail(
      String patientEmail,
      String patientFirstName,
      String patientLastName,
      int turnOrder,
      String serviceName,
      String date,
      String hour,
      String stablishmentName,
      String doctorFullName) {

    try {
      MimeMessage message = javaMailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setFrom("bravo.luis.1995@gmail.com");
      helper.setTo(patientEmail);
      helper.setSubject("Recordatorio: Su Turno #" + turnOrder + " es próximo");

      Context context = new Context();
      context.setVariable("patientFirstName", patientFirstName);
      context.setVariable("patientLastName", patientLastName);
      context.setVariable("turnOrder", turnOrder);
      context.setVariable("stablishmentName", stablishmentName);
      context.setVariable("serviceName", serviceName);
      context.setVariable("doctorFullName", doctorFullName);
      context.setVariable("date", date);
      context.setVariable("hour", hour);

      String htmlBody = templateEngine.process("recordatorio_cita", context);
      helper.setText(htmlBody, true);

      log.info("Correo enviado a {}", patientEmail);
      javaMailSender.send(message);
    } catch (Exception e) {
      log.error("Error enviando email de recordatorio: ", e);
    }
  }
}
