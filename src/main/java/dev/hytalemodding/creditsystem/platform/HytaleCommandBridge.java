package dev.hytalemodding.creditsystem.platform;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hytalemodding.creditsystem.CreditSystemPlugin;
import dev.hytalemodding.creditsystem.commands.CreditShopCommand;
import dev.hytalemodding.creditsystem.commands.CreditShopOpenCommand;
import dev.hytalemodding.creditsystem.commands.CreditsCommandCollection;
import dev.hytalemodding.creditsystem.service.CreditsService;

import java.util.Locale;

public final class HytaleCommandBridge implements HytalePlatformBridge {
    private final CreditSystemPlugin plugin;
    private final CreditsService creditsService;

    private boolean commandsRegistered;

    public HytaleCommandBridge(CreditSystemPlugin plugin, java.util.logging.Logger logger, CreditsService creditsService) {
        this.plugin = plugin;
        this.creditsService = creditsService;
    }

    @Override
    public void registerCreditsCommands(CreditsCommandCollection collection) {
        CommandRegistry registry = plugin.getCommandRegistry();
        registry.registerCommand(new CreditsRootCommand(plugin, creditsService, collection.currencyName(), this::commandsRegistered));
        this.commandsRegistered = true;
    }

    @Override
    public void registerCreditShopCommand(CreditShopCommand command) {
        CommandRegistry registry = plugin.getCommandRegistry();
        registry.registerCommand(new CreditShopOpenCommand(command.creditsService(), command.config(), command.logger()));
    }

    @Override
    public boolean commandsRegistered() {
        return commandsRegistered;
    }

    private static boolean hasAdmin(CommandSender sender, String subPermission) {
        return sender.hasPermission("creditsystem.admin") || sender.hasPermission(subPermission);
    }

    private static boolean canSeeAdminHelp(CommandSender sender) {
        return sender.hasPermission("creditsystem.admin")
                || sender.hasPermission("creditsystem.credits.give")
                || sender.hasPermission("creditsystem.credits.set")
                || sender.hasPermission("creditsystem.credits.remove");
    }

    private static final class CreditsRootCommand extends CommandBase {
        private final CreditSystemPlugin plugin;
        private final CreditsService creditsService;
        private final String currencyName;
        private final java.util.function.BooleanSupplier registrationSupplier;

        private final OptionalArg<String> actionArg;
        private final OptionalArg<PlayerRef> targetArg;
        private final OptionalArg<Integer> amountArg;

        private CreditsRootCommand(CreditSystemPlugin plugin, CreditsService creditsService, String currencyName, java.util.function.BooleanSupplier registrationSupplier) {
            super("credits", "Check and manage credits balances");
            this.plugin = plugin;
            this.creditsService = creditsService;
            this.currencyName = currencyName;
            this.registrationSupplier = registrationSupplier;
            this.addAliases("credit");

            this.actionArg = withOptionalArg("action", "Action: give, set, remove, debug, storage", ArgTypes.STRING);
            this.targetArg = withOptionalArg("player", "Target player", ArgTypes.PLAYER_REF);
            this.amountArg = withOptionalArg("amount", "Amount", ArgTypes.INTEGER);
        }

        @Override
        protected void executeSync(CommandContext context) {
            CommandSender sender = context.sender();
            String action = context.provided(actionArg) ? context.get(actionArg).toLowerCase(Locale.ROOT) : "";

            if (action.isBlank()) {
                if (!context.isPlayer()) {
                    context.sendMessage(Message.raw("Only players can run /credits without arguments."));
                    return;
                }
                if (!creditsService.isOnline()) {
                    context.sendMessage(Message.raw("Credits system unavailable."));
                    return;
                }
                long balance = creditsService.getBalance(sender.getUuid(), sender.getDisplayName());
                context.sendMessage(Message.raw(currencyName + ": " + balance));
                if (canSeeAdminHelp(sender)) {
                    context.sendMessage(Message.raw("Admin: /credits give <player> <amount> | /credits set <player> <amount> | /credits remove <player> <amount>"));
                }
                return;
            }

            if (action.equals("debug")) {
                if (!hasAdmin(sender, "creditsystem.credits.debug")) {
                    context.sendMessage(Message.raw("You do not have permission."));
                    return;
                }
                context.sendMessage(Message.raw("Plugin enabled: " + plugin.isEnabled()));
                context.sendMessage(Message.raw("Storage backend: " + creditsService.backendName()));
                context.sendMessage(Message.raw("DB online: " + creditsService.isOnline()));
                context.sendMessage(Message.raw("Commands registered: " + registrationSupplier.getAsBoolean()));
                context.sendMessage(Message.raw("Last error: " + creditsService.lastError()));
                return;
            }

            if (action.equals("storage")) {
                if (!hasAdmin(sender, "creditsystem.credits.storage")) {
                    context.sendMessage(Message.raw("You do not have permission."));
                    return;
                }
                context.sendMessage(Message.raw("Storage backend: " + creditsService.backendName()));
                context.sendMessage(Message.raw("Storage location: " + creditsService.location()));
                context.sendMessage(Message.raw("Storage online: " + creditsService.isOnline()));
                context.sendMessage(Message.raw("Storage last error: " + creditsService.lastError()));
                return;
            }

            if (!creditsService.isOnline()) {
                context.sendMessage(Message.raw("Credits system unavailable."));
                return;
            }

            if (!action.equals("give") && !action.equals("set") && !action.equals("remove")) {
                context.sendMessage(Message.raw("Usage: /credits give <player> <amount> | /credits set <player> <amount> | /credits remove <player> <amount>"));
                return;
            }

            if (!context.provided(targetArg) || !context.provided(amountArg)) {
                context.sendMessage(Message.raw("Usage: /credits give <player> <amount> | /credits set <player> <amount> | /credits remove <player> <amount>"));
                return;
            }

            PlayerRef target = context.get(targetArg);
            long amount = context.get(amountArg);
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
}
