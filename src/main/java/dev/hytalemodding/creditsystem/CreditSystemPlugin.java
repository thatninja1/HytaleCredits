package dev.hytalemodding.creditsystem;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.creditsystem.commands.CreditShopCommand;
import dev.hytalemodding.creditsystem.commands.CreditsCommandCollection;
import dev.hytalemodding.creditsystem.config.CreditConfig;
import dev.hytalemodding.creditsystem.db.CreditsRepository;
import dev.hytalemodding.creditsystem.db.CreditsRepositoryFactory;
import dev.hytalemodding.creditsystem.platform.HytaleCommandBridge;
import dev.hytalemodding.creditsystem.platform.HytalePlatformBridge;
import dev.hytalemodding.creditsystem.platform.NoopHytalePlatformBridge;
import dev.hytalemodding.creditsystem.service.CreditsService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main plugin class loaded by Hytale's Java plugin loader.
 */
public final class CreditSystemPlugin extends JavaPlugin {
    private final Logger logger = Logger.getLogger("CreditSystem");

    private CreditConfig config;
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
        logger.info("[CreditSystem] Enabling...");
        this.creditsService = new CreditsService();

        try {
            Files.createDirectories(Path.of("plugins", "CreditSystem"));
            this.config = CreditConfig.loadDefault(logger);

            CreditsRepository repository = initializeWithFallback(config);
            if (repository != null) {
                this.creditsService.setRepository(repository);
                logger.log(Level.INFO, "Credits storage online: backend={0}, location={1}",
                        new Object[]{creditsService.backendName(), creditsService.location()});
            }

            logger.info("[CreditSystem] Registering commands...");
            this.platformBridge = new HytaleCommandBridge(this, logger, creditsService);

            try {
                platformBridge.registerCreditsCommands(new CreditsCommandCollection(creditsService, config.currencyName()));
                logger.info("[CreditSystem] Registered /credits");
            } catch (Exception creditsRegisterError) {
                logger.log(Level.SEVERE, "Failed to register /credits", creditsRegisterError);
                this.platformBridge = new NoopHytalePlatformBridge();
            }

            try {
                if (platformBridge != null) {
                    platformBridge.registerCreditShopCommand(new CreditShopCommand(creditsService, config, logger));
                    logger.info("[CreditSystem] Registered /creditshop");
                }
            } catch (Exception creditShopRegisterError) {
                logger.log(Level.SEVERE, "Failed to register /creditshop. /credits remains available.", creditShopRegisterError);
            }

            logger.info("[CreditSystem] Enabled successfully");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to enable CreditSystem", e);
            creditsService.markOffline("unknown", "unknown", e.getMessage());
        }
    }

    private CreditsRepository initializeWithFallback(CreditConfig cfg) {
        String configuredType = cfg.storage().type().toLowerCase(Locale.ROOT);
        CreditsRepository primary = CreditsRepositoryFactory.create(cfg.storage(), logger, cfg.debug());

        logger.log(Level.INFO, "Selected storage backend: {0} ({1})", new Object[]{primary.backendName(), primary.location()});
        try {
            primary.initialize();
            return primary;
        } catch (Exception primaryError) {
            logger.log(Level.SEVERE,
                    "Failed to initialize configured storage backend '" + configuredType + "'. "
                            + "Check connectivity/credentials. Falling back to H2.",
                    primaryError);

            if ("h2".equalsIgnoreCase(configuredType)) {
                creditsService.markOffline("h2", primary.location(), primaryError.getMessage());
                return null;
            }

            CreditsRepository fallback = CreditsRepositoryFactory.createH2Fallback(cfg.storage(), logger, cfg.debug());
            logger.log(Level.INFO, "Attempting H2 fallback backend: {0}", fallback.location());
            try {
                fallback.initialize();
                logger.log(Level.WARNING, "Storage fallback applied. Running on H2 backend.");
                return fallback;
            } catch (Exception fallbackError) {
                logger.log(Level.SEVERE, "H2 fallback initialization also failed.", fallbackError);
                creditsService.markOffline("h2", fallback.location(), fallbackError.getMessage());
                return null;
            }
        }
    }

    public void onDisable() {
        if (creditsService != null) {
            creditsService.shutdown();
        }
        logger.log(Level.INFO, "CreditSystem disabled.");
    }

    public CreditConfig getCreditConfig() {
        return config;
    }

    public CreditsService getCreditsService() {
        return creditsService;
    }

    public boolean didRegisterCommands() {
        return platformBridge != null && platformBridge.commandsRegistered();
    }
}
