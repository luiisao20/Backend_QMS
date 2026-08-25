package com.devluis.types;

// Mirrors the distinction jsons/landing/insurers.json (private companies) and
// jsons/landing/public-insurance.json (IESS/ISSFA/ISSPOL/MSP) already draw on
// the marketing site, kept as a discriminator on ONE unified Insurer catalog
// instead of two parallel tables — see Insurer's own docblock for why.
public enum InsurerType {
  INSURER_PRIVATE,
  INSURER_PUBLIC
}
