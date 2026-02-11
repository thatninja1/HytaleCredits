package dev.hytalemodding.creditsystem.commands;

import java.util.UUID;

public record PlayerRef(UUID uuid, String name, boolean online) {
}
