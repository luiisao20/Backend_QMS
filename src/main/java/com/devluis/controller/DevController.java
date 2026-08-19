package com.devluis.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devluis.services.MailService;
import com.devluis.types.EmailBody;

import lombok.Data;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/dev")
@Data
public class DevController {
  private final MailService mailService;

  @PostMapping("/send-email")
  public ResponseEntity<?> sendEmail(@RequestBody EmailBody entity) {
    mailService.sendTestEmail(entity.getTo(), entity.getSubject(), entity.getText());
    return ResponseEntity.ok().body("OK");
  }

}
