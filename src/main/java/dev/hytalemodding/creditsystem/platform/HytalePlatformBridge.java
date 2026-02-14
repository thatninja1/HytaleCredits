package dev.hytalemodding.creditsystem.platform;

import dev.hytalemodding.creditsystem.commands.CreditShopCommand;
import dev.hytalemodding.creditsystem.commands.CreditsCommandCollection;

public interface HytalePlatformBridge {
    void registerCreditsCommands(CreditsCommandCollection collection);

    void registerCreditShopCommand(CreditShopCommand command);

    boolean commandsRegistered();
}
