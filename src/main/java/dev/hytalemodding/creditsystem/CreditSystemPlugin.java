package dev.hytalemodding.creditsystem;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
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
 * Main plugin class loaded by Hytale's Java plugin loader.
 */
public final class CreditSystemPlugin extends JavaPlugin {
    private final Logger logger = Logger.getLogger("CreditSystem");

    private CreditConfig config;
    private SqlCreditsRepository repository;
    private CreditsService creditsService;
    private HytalePlatformBridge platformBridge;

    public CreditSystemPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void start() {
        onEnable();
    }

    @Override
    protected void shutdown() {
        onDisable();
    }

    public void onEnable() {
        try {
            this.config = CreditConfig.loadDefault(logger);
            this.repository = new SqlCreditsRepository(config.database(), logger, config.debug());
            repository.initialize();
            this.creditsService = new CreditsService(repository);

            this.platformBridge = HytalePlatformBridge.tryCreate(logger);
            platformBridge.registerCreditsCommands(new CreditsCommandCollection(creditsService, config.currencyName()));
            platformBridge.registerCreditShopCommand(new CreditShopCommand(creditsService, config, logger));

            logger.log(Level.INFO, "CreditSystem enabled.");
        } catch (IOException | SQLException e) {
            logger.log(Level.SEVERE, "Failed to enable CreditSystem", e);
            throw new IllegalStateException("CreditSystem startup failed", e);
        }
    }

    public void onDisable() {
        if (repository != null) {
            repository.close();
        }
        logger.log(Level.INFO, "CreditSystem disabled.");
    }

    public CreditConfig getCreditConfig() {
        return config;
    }

    public CreditsService getCreditsService() {
        return creditsService;
    }
}
