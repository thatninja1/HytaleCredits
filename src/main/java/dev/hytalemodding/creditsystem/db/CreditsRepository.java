package dev.hytalemodding.creditsystem.db;

import java.util.UUID;

public interface CreditsRepository {
    long getBalance(UUID uuid, String name);

    void addCredits(UUID uuid, String name, long amount);

    void removeCredits(UUID uuid, String name, long amount);

    void setCredits(UUID uuid, String name, long amount);

    boolean tryPurchase(UUID uuid, String name, long price);

    void initialize();

    void shutdown();

    String backendName();

    String location();
}
