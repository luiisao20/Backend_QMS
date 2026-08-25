package com.devluis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devluis.entity.Branding;

// No custom query methods on purpose: the table holds at most one row, so
// the inherited findAll()/save() from JpaRepository are enough — see
// BrandingService. Keeping this repository to inherited-only methods means
// zero new JPQL surface for this feature.
public interface BrandingRepository extends JpaRepository<Branding, Long> {
}
