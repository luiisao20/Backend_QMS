package com.devluis.types;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthResponse {
  private String email;
  private String firstName;
  private String lastName;
  private String role;
  private UUID uuid;
  private String message;
}
