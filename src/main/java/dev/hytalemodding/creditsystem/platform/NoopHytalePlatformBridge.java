package dev.hytalemodding.creditsystem.platform;

import dev.hytalemodding.creditsystem.commands.CreditShopCommand;
import dev.hytalemodding.creditsystem.commands.CreditsCommandCollection;

final class NoopHytalePlatformBridge implements HytalePlatformBridge {
    @Override
    public void registerCreditsCommands(CreditsCommandCollection collection) {
        // no-op in compile-only environment
    }

    @Override
    public void registerCreditShopCommand(CreditShopCommand command) {
        // no-op in compile-only environment
    }
}
