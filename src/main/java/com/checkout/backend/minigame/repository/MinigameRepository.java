package com.checkout.backend.minigame.repository;

import com.checkout.backend.minigame.model.Minigame;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MinigameRepository extends JpaRepository<Minigame, Long> {
}