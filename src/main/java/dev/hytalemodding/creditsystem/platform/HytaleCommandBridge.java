package dev.hytalemodding.creditsystem.platform;

import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import dev.hytalemodding.creditsystem.CreditSystemPlugin;
import dev.hytalemodding.creditsystem.commands.CreditShopCommand;
import dev.hytalemodding.creditsystem.commands.CreditShopOpenCommand;
import dev.hytalemodding.creditsystem.commands.CreditsCommand;
import dev.hytalemodding.creditsystem.commands.CreditsCommandCollection;
import dev.hytalemodding.creditsystem.service.CreditsService;
import dev.hytalemodding.creditsystem.shop.CategoryShopLoader;

public final class HytaleCommandBridge implements HytalePlatformBridge {
    private final CreditSystemPlugin plugin;
    private final CreditsService creditsService;
    private final CategoryShopLoader shopLoader;

    private boolean commandsRegistered;

    public HytaleCommandBridge(CreditSystemPlugin plugin, java.util.logging.Logger logger, CreditsService creditsService, CategoryShopLoader shopLoader) {
        this.plugin = plugin;
        this.creditsService = creditsService;
        this.shopLoader = shopLoader;
    }

    @Override
    public void registerCreditsCommands(CreditsCommandCollection collection) {
        CommandRegistry registry = plugin.getCommandRegistry();
        registry.registerCommand(new CreditsCommand(plugin, creditsService, plugin::getCreditConfig, this::commandsRegistered));
        this.commandsRegistered = true;
    }

    @Override
    public void registerCreditShopCommand(CreditShopCommand command) {
        CommandRegistry registry = plugin.getCommandRegistry();
        registry.registerCommand(new CreditShopOpenCommand(command.creditsService(), plugin::getCreditConfig, shopLoader, command.logger()));
    }

    @Override
    public boolean commandsRegistered() {
        return commandsRegistered;
    }
}
