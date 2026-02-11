package dev.hytalemodding.creditsystem.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.creditsystem.CreditSystemPlugin;
import dev.hytalemodding.creditsystem.service.CreditsService;

import java.util.Locale;
import java.util.function.BooleanSupplier;

public final class CreditsCommand extends AbstractPlayerCommand {
    private final CreditSystemPlugin plugin;
    private final CreditsService creditsService;
    private final String currencyName;
    private final BooleanSupplier registrationSupplier;

    public CreditsCommand(CreditSystemPlugin plugin, CreditsService creditsService, String currencyName, BooleanSupplier registrationSupplier) {
        super("credits", "Check and manage credits balances");
        this.plugin = plugin;
        this.creditsService = creditsService;
        this.currencyName = currencyName;
        this.registrationSupplier = registrationSupplier;
        this.addAliases("credit");

        addUsageVariant(new CreditsAdminActionVariant(creditsService, currencyName));
        addUsageVariant(new CreditsInfoVariant(plugin, creditsService, registrationSupplier));
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> senderRef, PlayerRef senderPlayerRef, World world) {
        if (!creditsService.isOnline()) {
            context.sendMessage(Message.raw("Credits system unavailable."));
            return;
        }

        long balance = creditsService.getBalance(senderPlayerRef.getUuid(), senderPlayerRef.getUsername());
        context.sendMessage(Message.raw(currencyName + ": " + balance));
        if (canSeeAdminHelp(context.sender())) {
            context.sendMessage(Message.raw("Admin: /credits give <player> <amount> | /credits set <player> <amount> | /credits remove <player> <amount>"));
        }
    }

    private static boolean canSeeAdminHelp(CommandSender sender) {
        return sender.hasPermission("creditsystem.admin")
                || sender.hasPermission("creditsystem.credits.give")
                || sender.hasPermission("creditsystem.credits.set")
                || sender.hasPermission("creditsystem.credits.remove");
    }

    private static boolean hasAdmin(CommandSender sender, String subPermission) {
        return sender.hasPermission("creditsystem.admin") || sender.hasPermission(subPermission);
    }

    private static final class CreditsAdminActionVariant extends CommandBase {
        private final CreditsService creditsService;
        private final String currencyName;

        private final RequiredArg<String> actionArg;
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<Integer> amountArg;

        private CreditsAdminActionVariant(CreditsService creditsService, String currencyName) {
            super("Admin credits commands");
            this.creditsService = creditsService;
            this.currencyName = currencyName;
            this.actionArg = withRequiredArg("action", "give|set|remove", ArgTypes.STRING);
            this.playerArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
            this.amountArg = withRequiredArg("amount", "Amount", ArgTypes.INTEGER);
        }

        @Override
        protected void executeSync(CommandContext context) {
            String action = context.get(actionArg).toLowerCase(Locale.ROOT);
            PlayerRef target = context.get(playerArg);
            long amount = context.get(amountArg);
            CommandSender sender = context.sender();

            if (!creditsService.isOnline()) {
                context.sendMessage(Message.raw("Credits system unavailable."));
                return;
            }

            if (target == null) {
                context.sendMessage(Message.raw("Target player must be online."));
                return;
            }

            switch (action) {
                case "give" -> {
                    if (!hasAdmin(sender, "creditsystem.credits.give")) {
                        context.sendMessage(Message.raw("You do not have permission."));
                        return;
                    }
                    if (amount <= 0) {
                        context.sendMessage(Message.raw("Amount must be greater than 0."));
                        return;
                    }
                    creditsService.give(target.getUuid(), target.getUsername(), amount);
                    long balance = creditsService.getBalance(target.getUuid(), target.getUsername());
                    context.sendMessage(Message.raw("Gave " + amount + " " + currencyName + " to " + target.getUsername() + ". New balance: " + balance));
                }
                case "set" -> {
                    if (!hasAdmin(sender, "creditsystem.credits.set")) {
                        context.sendMessage(Message.raw("You do not have permission."));
                        return;
                    }
                    if (amount < 0) {
                        context.sendMessage(Message.raw("Amount must be 0 or greater."));
                        return;
                    }
                    creditsService.set(target.getUuid(), target.getUsername(), amount);
                    context.sendMessage(Message.raw("Set " + target.getUsername() + " " + currencyName + " to " + amount + "."));
                }
                case "remove" -> {
                    if (!hasAdmin(sender, "creditsystem.credits.remove")) {
                        context.sendMessage(Message.raw("You do not have permission."));
                        return;
                    }
                    if (amount <= 0) {
                        context.sendMessage(Message.raw("Amount must be greater than 0."));
                        return;
                    }
                    creditsService.remove(target.getUuid(), target.getUsername(), amount);
                    long balance = creditsService.getBalance(target.getUuid(), target.getUsername());
                    context.sendMessage(Message.raw("Removed " + amount + " " + currencyName + " from " + target.getUsername() + ". New balance: " + balance));
                }
                default -> context.sendMessage(Message.raw("Usage: /credits give <player> <amount> | /credits set <player> <amount> | /credits remove <player> <amount>"));
            }
        }
    }

    private static final class CreditsInfoVariant extends CommandBase {
        private final CreditSystemPlugin plugin;
        private final CreditsService creditsService;
        private final BooleanSupplier registrationSupplier;
        private final RequiredArg<String> actionArg;

        private CreditsInfoVariant(CreditSystemPlugin plugin, CreditsService creditsService, BooleanSupplier registrationSupplier) {
            super("Credits debug/storage");
            this.plugin = plugin;
            this.creditsService = creditsService;
            this.registrationSupplier = registrationSupplier;
            this.actionArg = withRequiredArg("action", "storage|debug", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(CommandContext context) {
            String action = context.get(actionArg).toLowerCase(Locale.ROOT);
            CommandSender sender = context.sender();

            if (!sender.hasPermission("creditsystem.admin")) {
                context.sendMessage(Message.raw("You do not have permission."));
                return;
            }

            switch (action) {
                case "storage" -> {
                    context.sendMessage(Message.raw("Storage backend: " + creditsService.backendName()));
                    context.sendMessage(Message.raw("Storage location: " + creditsService.location()));
                    context.sendMessage(Message.raw("Storage online: " + creditsService.isOnline()));
                    context.sendMessage(Message.raw("Storage last error: " + creditsService.lastError()));
                }
                case "debug" -> {
                    context.sendMessage(Message.raw("Plugin enabled: " + plugin.isEnabled()));
                    context.sendMessage(Message.raw("Storage backend: " + creditsService.backendName()));
                    context.sendMessage(Message.raw("DB online: " + creditsService.isOnline()));
                    context.sendMessage(Message.raw("Commands registered: " + registrationSupplier.getAsBoolean()));
                    context.sendMessage(Message.raw("Last error: " + creditsService.lastError()));
                }
                default -> context.sendMessage(Message.raw("Usage: /credits storage | /credits debug"));
            }
        }
    }
}
