package com.devluis.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import com.devluis.dto.ClinicalAccessLogDTO;
import com.devluis.services.ClinicalAccessLogService;

import lombok.Data;

/**
 * "reportes/auditoria-hc": who read which patient's clinical data. ROLE_ADMIN
 * only — enforced entirely by GlobalConfig's URL matcher, same idiom as
 * BlockReasonController/HolidayController/TimeOffController (no per-record
 * nuance here, so no {@code @PreAuthorize} needed: the whole resource is
 * flatly admin-only). Deliberately no controller test, mirroring the
 * documented precedent that those three sibling controllers also have none.
 */
@RestController
@RequestMapping("/api/clinical-access-logs")
@Data
public class ClinicalAccessLogController {

  private final ClinicalAccessLogService clinicalAccessLogService;

  @GetMapping
  public Page<ClinicalAccessLogDTO> getAll(
      @RequestParam(required = false) UUID patientId,
      @PageableDefault(size = 10) Pageable pageable) {
    return clinicalAccessLogService.getAll(patientId, pageable);
  }
}
