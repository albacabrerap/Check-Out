package com.checkout.backend.minigame.repository;

import com.checkout.backend.minigame.model.Minigame;
import com.checkout.backend.minigame.model.MinigameStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MinigameRepository extends JpaRepository<Minigame, Long> {
    List<Minigame> findByStatusOrderByTitleAsc(MinigameStatus status);
    Optional<Minigame> findByIdAndStatus(Long id, MinigameStatus status);
    boolean existsByTitleIgnoreCase(String title);
}