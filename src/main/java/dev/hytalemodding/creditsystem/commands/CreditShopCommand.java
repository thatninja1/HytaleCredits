package dev.hytalemodding.creditsystem.commands;

import dev.hytalemodding.creditsystem.service.CreditsService;

import java.util.logging.Logger;

public final class CreditShopCommand {
    private final CreditsService creditsService;
    private final Logger logger;

    public CreditShopCommand(CreditsService creditsService, Logger logger) {
        this.creditsService = creditsService;
        this.logger = logger;
    }

    public CreditsService creditsService() {
        return creditsService;
    }

    public Logger logger() {
        return logger;
    }
}
