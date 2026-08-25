package com.devluis.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Clinic-wide identity: name, logo, brand colors and public contact
// details — exactly the fields the admin panel and the public landing need
// to stop being hardcoded. Field names deliberately mirror
// jsons/landing/site.json's `brand`/`contact` blocks (`name`, `phone`,
// `emergencyPhone`, `whatsapp`, `email`) instead of inventing new
// vocabulary for the same concepts. `nav`/`footer`/`legal` from that same
// file are NOT modeled here on purpose: those are page content (link
// lists, legal disclaimers), not identity config, and are out of scope for
// this change.
//
// SINGLETON BY CONVENTION, not by schema: there is exactly one clinic, so
// exactly one row is ever expected in this table. This is enforced entirely
// in BrandingService (find-the-one-row-if-any, then update it — never a
// second insert) rather than by hardcoding a fixed primary key, so this
// entity stays a normal GenerationType.IDENTITY id like every other entity
// in this codebase. See BrandingServiceTest for the test that proves a
// second write updates the existing row instead of inserting a new one.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "branding")
@Entity
public class Branding {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  private String logoUrl;

  private String primaryColor;

  private String secondaryColor;

  private String phone;

  private String emergencyPhone;

  private String whatsapp;

  private String email;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime updatedAt;
}
