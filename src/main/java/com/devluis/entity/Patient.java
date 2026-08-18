package com.devluis.entity;

import java.time.LocalDate;
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
@Table(name = "patients")
@Entity
public class Patient {
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

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private Role role = Role.ROLE_PATIENT;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;

  @Column(nullable = false, unique = true)
  private String ci;

  @Column(nullable = false)
  private LocalDate birthday;

  @Enumerated(EnumType.STRING)
  private Gender gender;

  @Column(columnDefinition = "text")
  private String address;

  private String phone;

  private String emergencyContactPhone;

  private String emergencyContactName;

  @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL)
  private List<Turn> turns;
}
