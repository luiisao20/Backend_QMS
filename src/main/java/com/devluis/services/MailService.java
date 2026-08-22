package com.devluis.services;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import lombok.Data;

@Service
@Data
public class MailService {
  private final JavaMailSender javaMailSender;

  public void sendTestEmail(String to, String subject, String text) {
    SimpleMailMessage message = new SimpleMailMessage();

    message.setFrom("bravo.luis.1995@gmail.com");
    message.setTo(to);
    message.setSubject(subject);
    message.setText(text);

    javaMailSender.send(message);
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
      String doctorFullName
  ) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom("bravo.luis.1995@gmail.com");
    message.setTo(patientEmail);
    message.setSubject("Confirmación de Turno #" + turnOrder + " - " + serviceName);

    String body = String.format(
        "Estimado(a) %s %s (C.I: %s),\n\n"
        + "Su turno ha sido creado exitosamente. A continuación los detalles:\n\n"
        + "  📋 Número de turno: %d\n"
        + "  🏥 Establecimiento: %s\n"
        + "  🩺 Servicio: %s\n"
        + "  👨‍⚕️ Doctor: %s\n"
        + "  📅 Fecha: %s\n"
        + "  🕐 Hora: %s\n\n"
        + "Por favor, preséntese con anticipación.\n\n"
        + "Saludos cordiales,\n"
        + "Sistema de Gestión de Turnos (QMS)",
        patientFirstName, patientLastName, patientCi,
        turnOrder,
        stablishmentName,
        serviceName,
        doctorFullName,
        date,
        hour
    );

    message.setText(body);
    javaMailSender.send(message);
  }
}
