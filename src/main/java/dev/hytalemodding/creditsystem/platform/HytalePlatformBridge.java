package dev.hytalemodding.creditsystem.platform;

import dev.hytalemodding.creditsystem.commands.CreditShopCommand;
import dev.hytalemodding.creditsystem.commands.CreditsCommandCollection;

import java.util.logging.Logger;

/**
 * Runtime bridge point where concrete command/UI registration can be hooked into Hytale API.
 */
public interface HytalePlatformBridge {
    static HytalePlatformBridge tryCreate(Logger logger) {
        logger.info("Using no-op platform bridge. Wire this to Hytale command registration in production.");
        return new NoopHytalePlatformBridge();
    }

    void registerCreditsCommands(CreditsCommandCollection collection);

    void registerCreditShopCommand(CreditShopCommand command);
}
