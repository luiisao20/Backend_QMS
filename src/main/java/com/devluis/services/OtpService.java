package com.devluis.services;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.devluis.types.OtpData;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OtpService {
  private final ConcurrentHashMap<String, OtpData> otpStore = new ConcurrentHashMap<>();

  public String generateOtp() {
    return String.format("%06d", new SecureRandom().nextInt(999999));
  }

  public void saveOtp(String email, String otp) {
    otpStore.put(email, OtpData.builder()
        .expiresAt(LocalDateTime.now().plusMinutes(10))
        .intentosFallidos(0)
        .otp(otp)
        .build());
  }

  public OtpData getOtp(String cedula) {
    return otpStore.get(cedula);
  }

  public void deleteOtp(String cedula) {
    otpStore.remove(cedula);
  }

  public boolean isBlocked(String cedula) {
    OtpData data = otpStore.get(cedula);
    return data != null && data.excedioIntentos();
  }

  public boolean validate(String email, String inputOtp) {
    OtpData data = otpStore.get(email);

    if (data == null)
      return false;

    if (!data.getOtp().equals(inputOtp)) {
      data.setIntentosFallidos(data.getIntentosFallidos() + 1);
      return false;
    }
    return true;
  }

  @Scheduled(fixedRate = 5 * 60 * 1000)
  public void cleanExpiredOtps() {
    int before = otpStore.size();

    otpStore.entrySet().removeIf(entry -> entry.getValue().isExpired());

    int removed = before - otpStore.size();
    if (removed > 0) {
      log.info("OTPs expirados eliminados: {}", removed);
    }
  }
}
