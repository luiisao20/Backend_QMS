package com.devluis.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "stablishments")
@Entity
public class Stablishment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String address;

  @OneToMany(cascade = CascadeType.ALL, mappedBy = "stablishment")
  @JsonIgnore
  private List<Operator> operators;

  @ManyToMany
  @JoinTable(name = "stablishment_has_doctors", joinColumns = @JoinColumn(name = "stablishment_id"), inverseJoinColumns = @JoinColumn(name = "doctor_id"))
  private List<Doctor> doctors;

  @OneToMany(mappedBy = "stablishment", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Schedule> schedules;

  @ManyToMany
  @JoinTable(
      name = "stablishment_has_services",
      joinColumns = @JoinColumn(name = "stablishment_id"),
      inverseJoinColumns = @JoinColumn(name = "service_id")
  )
  private List<Servicio> services;
}
