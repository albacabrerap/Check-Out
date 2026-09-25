package com.checkout.backend.projection.repository;

import com.checkout.backend.projection.model.Projection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectionRepository extends JpaRepository<Projection, Long> {
}
