package dev.hytalemodding.creditsystem.service;

import dev.hytalemodding.creditsystem.db.SqlCreditsRepository;

import java.sql.SQLException;
import java.util.UUID;

public final class CreditsService {
    private final SqlCreditsRepository repository;

    public CreditsService(SqlCreditsRepository repository) {
        this.repository = repository;
    }

    public long getBalance(UUID playerUuid, String playerName) {
        try {
            return repository.getBalance(playerUuid, playerName);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to fetch balance", e);
        }
    }

    public void give(UUID playerUuid, String playerName, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        try {
            repository.addCredits(playerUuid, playerName, amount);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to add credits", e);
        }
    }

    public void remove(UUID playerUuid, String playerName, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        try {
            repository.removeCredits(playerUuid, playerName, amount);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove credits", e);
        }
    }

    public void set(UUID playerUuid, String playerName, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be greater than or equal to zero.");
        }
        try {
            repository.setCredits(playerUuid, playerName, amount);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to set credits", e);
        }
    }
}
