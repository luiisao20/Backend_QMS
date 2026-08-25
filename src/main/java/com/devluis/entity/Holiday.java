package com.devluis.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// A date on which a clinic (or one specific establishment) does not operate.
//
// stablishment is deliberately NULLABLE instead of required: a chain-wide
// holiday (national holiday) applies to every Stablishment, while a
// site-specific closure (renovation, local event) applies to exactly one.
// NULL = global (every establishment), non-null = that establishment only.
// This covers both cases from HolidayService.generateAffectedScheduleIds
// with a single column instead of a many-to-many join table, at the cost of
// needing one row per establishment for a holiday that applies to "most, but
// not all" sites — accepted as a reasonable simplification, see apply report.
//
// No recurrence rule (e.g. "every December 25th") is modelled: this is a
// PER-YEAR row. Recurrence (leap years, moving holidays, per-locale
// variation) is real complexity with no proven need yet; re-creating one row
// a year through this same CRUD screen is simple, honest, and trivially
// queryable by a plain `date` column. See apply report for this tradeoff.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "holidays")
@Entity
public class Holiday {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private LocalDate date;

  // Specific label for this holiday occurrence, e.g. "Navidad" — distinct
  // from `reason`, which is the general BlockReason category (e.g. "Feriado
  // nacional") shared across many Holiday/TimeOff rows.
  @Column(nullable = false)
  private String description;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "stablishment_id")
  private Stablishment stablishment;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reason_id", nullable = false)
  private BlockReason reason;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
