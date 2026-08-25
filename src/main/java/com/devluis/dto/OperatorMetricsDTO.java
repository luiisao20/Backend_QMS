package com.devluis.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-operator row for GET /api/metrics/employees.
 *
 * <p>Turn.operator is overwritten by whichever staff member last acted on a
 * turn (check-in, start-treatment, cancel, reassign, staff booking) — it is
 * not an audit trail. turnsHandled / cancelled are therefore a best-effort
 * proxy for staff activity, not a full action log.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OperatorMetricsDTO {
  private UUID operatorId;
  private String firstName;
  private String lastName;
  private long turnsHandled;
  private long cancelled;
}
