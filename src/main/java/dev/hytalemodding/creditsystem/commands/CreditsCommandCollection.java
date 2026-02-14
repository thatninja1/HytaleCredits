package dev.hytalemodding.creditsystem.commands;

import dev.hytalemodding.creditsystem.service.CreditsService;

import java.util.Locale;
import java.util.Objects;

public final class CreditsCommandCollection {
    private final CreditsService creditsService;
    private final String currencyName;

    public CreditsCommandCollection(CreditsService creditsService, String currencyName) {
        this.creditsService = Objects.requireNonNull(creditsService, "creditsService");
        this.currencyName = currencyName;
    }

    public String currencyName() {
        return currencyName;
    }

    public void executeBalance(CommandSender sender, PlayerRef self) {
        if (!sender.isPlayer()) {
            sender.sendMessage("Only players can run /credits without arguments.");
            return;
        }

        if (!creditsService.isOnline()) {
            sender.sendMessage("Credits system unavailable.");
            return;
        }

        long balance = creditsService.getBalance(self.uuid(), self.name());
        sender.sendMessage(currencyName + ": " + balance);
    }

    public void executeAdmin(CommandSender sender, String subcommand, PlayerRef target, long amount) {
        String normalized = subcommand.toLowerCase(Locale.ROOT);
        if (normalized.equals("storage")) {
            executeStorage(sender);
            return;
        }

        if (!creditsService.isOnline()) {
            sender.sendMessage("Credits system unavailable.");
            return;
        }

        if (target == null || !target.online()) {
            sender.sendMessage("Target player must be online.");
            return;
        }

        switch (normalized) {
            case "give" -> executeGive(sender, target, amount);
            case "set" -> executeSet(sender, target, amount);
            case "remove" -> executeRemove(sender, target, amount);
            default -> sender.sendMessage("Usage: /credits give|set|remove <player> <amount> OR /credits storage");
        }
    }

    public void executeStorage(CommandSender sender) {
        sender.sendMessage("Storage backend: " + creditsService.backendName());
        sender.sendMessage("Storage location: " + creditsService.location());
        sender.sendMessage("Storage online: " + creditsService.isOnline());
        sender.sendMessage("Storage last error: " + creditsService.lastError());
    }

    private void executeGive(CommandSender sender, PlayerRef target, long amount) {
        if (!sender.hasPermission("creditsystem.credits.give")) {
            sender.sendMessage("You do not have permission.");
            return;
        }
        if (amount <= 0) {
            sender.sendMessage("Amount must be greater than 0.");
            return;
        }

        creditsService.give(target.uuid(), target.name(), amount);
        long balance = creditsService.getBalance(target.uuid(), target.name());
        sender.sendMessage("Gave " + amount + " " + currencyName + " to " + target.name() + ". New balance: " + balance);
    }

    private void executeSet(CommandSender sender, PlayerRef target, long amount) {
        if (!sender.hasPermission("creditsystem.credits.set")) {
            sender.sendMessage("You do not have permission.");
            return;
        }
        if (amount < 0) {
            sender.sendMessage("Amount must be 0 or greater.");
            return;
        }

        creditsService.set(target.uuid(), target.name(), amount);
        sender.sendMessage("Set " + target.name() + " " + currencyName + " to " + amount + ".");
    }

    private void executeRemove(CommandSender sender, PlayerRef target, long amount) {
        if (!sender.hasPermission("creditsystem.credits.remove")) {
            sender.sendMessage("You do not have permission.");
            return;
        }
        if (amount <= 0) {
            sender.sendMessage("Amount must be greater than 0.");
            return;
        }

        creditsService.remove(target.uuid(), target.name(), amount);
        long balance = creditsService.getBalance(target.uuid(), target.name());
        sender.sendMessage("Removed " + amount + " " + currencyName + " from " + target.name() + ". New balance: " + balance);
    }
}
