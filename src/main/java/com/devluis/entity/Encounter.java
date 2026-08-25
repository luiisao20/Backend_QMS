package com.devluis.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// One clinical consultation. Relation to Turn is ONE-TO-ONE and OWNED here
// (turn_id is unique, not nullable): a Turn is a scheduling fact (a slot was
// booked), an Encounter is the clinical fact of what happened during that
// slot — at most one per Turn. The inverse side is deliberately NOT added to
// Turn itself (no `@OneToOne(mappedBy = "turn") Encounter encounter` field
// there): Turn is used everywhere in this codebase (turn boards, websocket
// broadcasts, metrics) and most Turns never reach TURN_TREATED, so forcing
// every existing Turn read path to consider a nullable Encounter would be
// unnecessary blast radius for a purely additive feature. Look it up via
// EncounterRepository.existsByTurnId/findByTurnId instead.
//
// Creation is only allowed once the Turn is TURN_TREATED (see
// EncounterService#create) — this is also the answer to "what happens if the
// turn is cancelled": a cancelled turn can never reach TURN_TREATED, and
// TurnService#cancelTurn/#cancelTurnByStaff both already refuse to cancel a
// turn that is already TURN_TREATED, so once an Encounter exists its Turn is
// permanently frozen out of cancellation by the EXISTING turn state machine
// — no new mechanism was needed here to protect that invariant.
//
// Vitals (blood pressure, heart rate, weight, etc.) are DELIBERATELY not
// modelled yet: the right shape (fixed columns vs. a per-specialty line-item
// table like PrescriptionItem) has no driving requirement today — no screen,
// no field list, no consumer — and guessing it now risks a wrong shape that
// has to be migrated later. See apply report.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "encounters")
@Entity
public class Encounter {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "turn_id", nullable = false, unique = true)
  private Turn turn;

  @Column(columnDefinition = "text", nullable = false)
  private String reasonForVisit;

  // Nullable on purpose: reasonForVisit and diagnosis are the two required
  // "bookend" facts of a completed consultation; clinicalNotes may
  // legitimately be expanded/corrected afterwards (see EncounterService's
  // update()), so it is not forced to exist at creation time the way the
  // other two are.
  @Column(columnDefinition = "text")
  private String clinicalNotes;

  @Column(columnDefinition = "text", nullable = false)
  private String diagnosis;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
