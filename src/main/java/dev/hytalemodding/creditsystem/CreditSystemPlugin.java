package dev.hytalemodding.creditsystem;

import dev.hytalemodding.creditsystem.commands.CreditShopCommand;
import dev.hytalemodding.creditsystem.commands.CreditsCommandCollection;
import dev.hytalemodding.creditsystem.config.CreditConfig;
import dev.hytalemodding.creditsystem.db.SqlCreditsRepository;
import dev.hytalemodding.creditsystem.platform.HytalePlatformBridge;
import dev.hytalemodding.creditsystem.service.CreditsService;

import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CreditSystem plugin entrypoint.
 *
 * <p>This class keeps framework coupling minimal so the project can compile in environments
 * where only API stubs are available. A concrete runtime bridge should call {@link #onEnable()}
 * and {@link #onDisable()} from the server lifecycle.</p>
 */
public final class CreditSystemPlugin {
    private final Logger logger = Logger.getLogger("CreditSystem");

    private CreditConfig config;
    private SqlCreditsRepository repository;
    private CreditsService creditsService;
    private HytalePlatformBridge platformBridge;

    public void onEnable() {
        try {
            this.config = CreditConfig.loadDefault(logger);
            this.repository = new SqlCreditsRepository(config.database(), logger, config.debug());
            repository.initialize();
            this.creditsService = new CreditsService(repository);

            this.platformBridge = HytalePlatformBridge.tryCreate(logger);
            platformBridge.registerCreditsCommands(new CreditsCommandCollection(creditsService, config.currencyName()));
            platformBridge.registerCreditShopCommand(new CreditShopCommand(creditsService, config, logger));

            logger.info("CreditSystem enabled.");
        } catch (IOException | SQLException e) {
            logger.log(Level.SEVERE, "Failed to enable CreditSystem", e);
            throw new IllegalStateException("CreditSystem startup failed", e);
        }
    }

    public void onDisable() {
        if (repository != null) {
            repository.close();
        }
        logger.info("CreditSystem disabled.");
    }

    public CreditConfig getConfig() {
        return config;
    }

    public CreditsService getCreditsService() {
        return creditsService;
    }
}
