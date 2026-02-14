package dev.hytalemodding.creditsystem.commands;

public interface CommandSender {
    String name();

    boolean isPlayer();

    boolean hasPermission(String permission);

    void sendMessage(String message);
}
