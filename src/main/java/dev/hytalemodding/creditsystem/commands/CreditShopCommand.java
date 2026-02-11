package dev.hytalemodding.creditsystem.commands;

import dev.hytalemodding.creditsystem.config.CreditConfig;
import dev.hytalemodding.creditsystem.service.CreditsService;
import dev.hytalemodding.creditsystem.ui.CreditShopPage;

import java.util.logging.Logger;

public final class CreditShopCommand {
    private final CreditsService creditsService;
    private final CreditConfig config;
    private final Logger logger;

    public CreditShopCommand(CreditsService creditsService, CreditConfig config, Logger logger) {
        this.creditsService = creditsService;
        this.config = config;
        this.logger = logger;
    }

    public CreditShopPage createPage(PlayerRef playerRef) {
        return new CreditShopPage(playerRef, creditsService, config, logger);
    }
}
