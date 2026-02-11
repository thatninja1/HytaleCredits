package dev.hytalemodding.creditsystem.platform;

import dev.hytalemodding.creditsystem.commands.CreditShopCommand;
import dev.hytalemodding.creditsystem.commands.CreditsCommandCollection;

public final class NoopHytalePlatformBridge implements HytalePlatformBridge {
    private boolean commandsRegistered;

    @Override
    public void registerCreditsCommands(CreditsCommandCollection collection) {
        this.commandsRegistered = false;
    }

    @Override
    public void registerCreditShopCommand(CreditShopCommand command) {
        this.commandsRegistered = false;
    }

    @Override
    public boolean commandsRegistered() {
        return commandsRegistered;
    }
}
