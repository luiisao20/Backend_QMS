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
}
