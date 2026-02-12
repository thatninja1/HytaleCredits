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
import dev.hytalemodding.creditsystem.config.CreditConfig;
import dev.hytalemodding.creditsystem.service.CreditsService;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class CreditsCommand extends AbstractPlayerCommand {
    private final CreditSystemPlugin plugin;
    private final CreditsService creditsService;
    private final Supplier<CreditConfig> configSupplier;
    private final BooleanSupplier registrationSupplier;

    public CreditsCommand(CreditSystemPlugin plugin,
                          CreditsService creditsService,
                          Supplier<CreditConfig> configSupplier,
                          BooleanSupplier registrationSupplier) {
        super("credits", "Check and manage credits balances");
        this.plugin = plugin;
        this.creditsService = creditsService;
        this.configSupplier = configSupplier;
        this.registrationSupplier = registrationSupplier;
        this.addAliases("credit");

        addUsageVariant(new CreditsAdminActionVariant(creditsService, this::currencyName));
        addUsageVariant(new CreditsInfoVariant(plugin, creditsService, this::currencyName, registrationSupplier));
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> senderRef, PlayerRef senderPlayerRef, World world) {
        sendHelpMenu(context, context.sender());
    }

    private String currencyName() {
        CreditConfig cfg = configSupplier.get();
        return cfg == null ? "Credits" : cfg.currencyName();
    }

    private static boolean canSeeAdminHelp(CommandSender sender) {
        return sender.hasPermission("creditsystem.admin")
                || sender.hasPermission("creditsystem.credits.give")
                || sender.hasPermission("creditsystem.credits.set")
                || sender.hasPermission("creditsystem.credits.remove")
                || sender.hasPermission("creditsystem.credits.reload");
    }

    private static boolean hasAdmin(CommandSender sender, String subPermission) {
        return sender.hasPermission("creditsystem.admin") || sender.hasPermission(subPermission);
    }

    private void sendHelpMenu(CommandContext context, CommandSender sender) {
        context.sendMessage(Message.raw("Credits Commands:"));
        context.sendMessage(Message.raw("- /credits bal (alias: /credits balance)"));
        if (canSeeAdminHelp(sender)) {
            context.sendMessage(Message.raw("ADMIN ONLY:"));
            context.sendMessage(Message.raw("- /credits give <player> <amount>"));
            context.sendMessage(Message.raw("- /credits remove <player> <amount>"));
            context.sendMessage(Message.raw("- /credits set <player> <amount>"));
            context.sendMessage(Message.raw("- /credits reload"));
        }
    }

    private record SenderIdentity(UUID uuid, String username) {
    }

    private static SenderIdentity resolveSenderIdentity(CommandSender sender) {
        try {
            Method playerRefMethod = sender.getClass().getMethod("playerRef");
            Object playerRefObj = playerRefMethod.invoke(sender);
            if (playerRefObj instanceof PlayerRef playerRef) {
                return new SenderIdentity(playerRef.getUuid(), playerRef.getUsername());
            }
        } catch (Exception ignored) {
        }

        try {
            Method getPlayerRefMethod = sender.getClass().getMethod("getPlayerRef");
            Object playerRefObj = getPlayerRefMethod.invoke(sender);
            if (playerRefObj instanceof PlayerRef playerRef) {
                return new SenderIdentity(playerRef.getUuid(), playerRef.getUsername());
            }
        } catch (Exception ignored) {
        }

        try {
            Method uuidMethod = sender.getClass().getMethod("getUuid");
            Method usernameMethod = sender.getClass().getMethod("getUsername");
            Object uuidObj = uuidMethod.invoke(sender);
            Object usernameObj = usernameMethod.invoke(sender);
            if (uuidObj instanceof UUID uuid && usernameObj instanceof String username) {
                return new SenderIdentity(uuid, username);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static final class CreditsAdminActionVariant extends CommandBase {
        private final CreditsService creditsService;
        private final Supplier<String> currencyNameSupplier;

        private final RequiredArg<String> actionArg;
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<Integer> amountArg;

        private CreditsAdminActionVariant(CreditsService creditsService, Supplier<String> currencyNameSupplier) {
            super("Admin credits commands");
            this.creditsService = creditsService;
            this.currencyNameSupplier = currencyNameSupplier;
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
            String currencyName = currencyNameSupplier.get();

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
        private final Supplier<String> currencyNameSupplier;
        private final BooleanSupplier registrationSupplier;
        private final RequiredArg<String> actionArg;

        private CreditsInfoVariant(CreditSystemPlugin plugin,
                                   CreditsService creditsService,
                                   Supplier<String> currencyNameSupplier,
                                   BooleanSupplier registrationSupplier) {
            super("Credits actions");
            this.plugin = plugin;
            this.creditsService = creditsService;
            this.currencyNameSupplier = currencyNameSupplier;
            this.registrationSupplier = registrationSupplier;
            this.actionArg = withRequiredArg("action", "bal|balance|storage|debug|reload", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(CommandContext context) {
            String action = context.get(actionArg).toLowerCase(Locale.ROOT);
            CommandSender sender = context.sender();

            switch (action) {
                case "bal", "balance" -> {
                    if (!creditsService.isOnline()) {
                        context.sendMessage(Message.raw("Credits system unavailable."));
                        return;
                    }

                    SenderIdentity identity = resolveSenderIdentity(sender);
                    if (identity == null) {
                        context.sendMessage(Message.raw("This command can only be run by a player."));
                        return;
                    }

                    long balance = creditsService.getBalance(identity.uuid(), identity.username());
                    context.sendMessage(Message.raw(currencyNameSupplier.get() + ": " + balance));
                }
                case "storage" -> {
                    if (!sender.hasPermission("creditsystem.admin")) {
                        context.sendMessage(Message.raw("You do not have permission."));
                        return;
                    }
                    context.sendMessage(Message.raw("Storage backend: " + creditsService.backendName()));
                    context.sendMessage(Message.raw("Storage location: " + creditsService.location()));
                    context.sendMessage(Message.raw("Storage online: " + creditsService.isOnline()));
                    context.sendMessage(Message.raw("Storage last error: " + creditsService.lastError()));
                }
                case "debug" -> {
                    if (!sender.hasPermission("creditsystem.admin")) {
                        context.sendMessage(Message.raw("You do not have permission."));
                        return;
                    }
                    context.sendMessage(Message.raw("Plugin enabled: " + plugin.isEnabled()));
                    context.sendMessage(Message.raw("Storage backend: " + creditsService.backendName()));
                    context.sendMessage(Message.raw("DB online: " + creditsService.isOnline()));
                    context.sendMessage(Message.raw("Commands registered: " + registrationSupplier.getAsBoolean()));
                    context.sendMessage(Message.raw("Last error: " + creditsService.lastError()));
                }
                case "reload" -> {
                    if (!(sender.hasPermission("creditsystem.admin") || sender.hasPermission("creditsystem.credits.reload"))) {
                        context.sendMessage(Message.raw("You do not have permission."));
                        return;
                    }

                    CreditSystemPlugin.ReloadResult reload = plugin.reloadPluginData();
                    if (reload.success()) {
                        context.sendMessage(Message.raw("[CreditSystem] Reloaded config.json and " + reload.loadedShopFiles() + " shop file(s)."));
                        context.sendMessage(Message.raw("[CreditSystem] Theme updated. Close and reopen /creditshop to see changes."));
                    } else {
                        context.sendMessage(Message.raw("[CreditSystem] Reload failed: " + reload.reason()));
                        if (!reload.failures().isEmpty()) {
                            for (Map.Entry<String, String> failure : reload.failures().entrySet()) {
                                context.sendMessage(Message.raw(" - " + failure.getKey() + " -> " + failure.getValue()));
                            }
                        }
                    }
                }
                default -> context.sendMessage(Message.raw("Usage: /credits bal | /credits balance | /credits storage | /credits debug | /credits reload"));
            }
        }
    }
}
