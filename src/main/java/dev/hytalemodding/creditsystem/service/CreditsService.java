package dev.hytalemodding.creditsystem.service;

import dev.hytalemodding.creditsystem.db.CreditsRepository;

import java.util.UUID;

public final class CreditsService {
    private CreditsRepository repository;
    private boolean dbOnline;
    private String backendName;
    private String location;
    private String lastError;

    public CreditsService() {
        this.dbOnline = false;
        this.backendName = "unknown";
        this.location = "unknown";
        this.lastError = "Not initialized";
    }

    public void setRepository(CreditsRepository repository) {
        this.repository = repository;
        this.backendName = repository.backendName();
        this.location = repository.location();
        this.dbOnline = true;
        this.lastError = "none";
    }

    public void markOffline(String backendName, String location, String errorMessage) {
        this.repository = null;
        this.backendName = backendName;
        this.location = location;
        this.dbOnline = false;
        this.lastError = errorMessage;
    }

    public boolean isOnline() {
        return dbOnline && repository != null;
    }

    public String backendName() {
        return backendName;
    }

    public String location() {
        return location;
    }

    public String lastError() {
        return lastError;
    }

    public long getBalance(UUID playerUuid, String playerName) {
        requireOnline();
        return repository.getBalance(playerUuid, playerName);
    }

    public void give(UUID playerUuid, String playerName, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        requireOnline();
        repository.addCredits(playerUuid, playerName, amount);
    }

    public void remove(UUID playerUuid, String playerName, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        requireOnline();
        repository.removeCredits(playerUuid, playerName, amount);
    }

    public void set(UUID playerUuid, String playerName, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be greater than or equal to zero.");
        }
        requireOnline();
        repository.setCredits(playerUuid, playerName, amount);
    }

    public void shutdown() {
        if (repository != null) {
            repository.shutdown();
        }
    }

    private void requireOnline() {
        if (!isOnline()) {
            throw new IllegalStateException("Credits system unavailable");
        }
    }
}
