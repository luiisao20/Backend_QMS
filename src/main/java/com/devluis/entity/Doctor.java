package com.devluis.entity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.devluis.types.Gender;
import com.devluis.types.Role;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "doctors")
@Entity
public class Doctor {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID uuid;

  @Column(unique = true, nullable = false)
  private String email;

  @Column(nullable = false)
  private String password;

  @Column(nullable = false)
  private String firstName;

  @Column(nullable = false)
  private String lastName;

  @Column(nullable = false)
  private String speciality;

  @Enumerated(EnumType.STRING)
  private Gender gender;

  @Column(nullable = false, unique = true)
  private String ci;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private Role role = Role.ROLE_DOCTOR;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;

  @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Schedule> schedules;

  @ManyToMany
  @JoinTable(name = "stablishment_has_doctors", joinColumns = @JoinColumn(name = "doctor_id"), inverseJoinColumns = @JoinColumn(name = "stablishment_id"))
  private List<Stablishment> stablishments;

  @ManyToMany
  @JoinTable(name = "doctor_has_services", joinColumns = @JoinColumn(name = "doctor_id"), inverseJoinColumns = @JoinColumn(name = "service_id"))
  private List<Servicio> services;
}
