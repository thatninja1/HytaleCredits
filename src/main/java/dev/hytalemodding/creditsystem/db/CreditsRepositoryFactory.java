package dev.hytalemodding.creditsystem.db;

import dev.hytalemodding.creditsystem.config.CreditConfig;

import java.nio.file.Path;
import java.util.Locale;
import java.util.logging.Logger;

public final class CreditsRepositoryFactory {
    private CreditsRepositoryFactory() {
    }

    public static CreditsRepository create(CreditConfig.StorageSettings storage, Logger logger, boolean debug) {
        String type = storage.type().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "mysql" -> new MySqlCreditsRepository(storage.mysql(), logger, debug);
            case "mariadb" -> new MariaDbCreditsRepository(storage.mariadb(), logger, debug);
            case "postgresql" -> new PostgresCreditsRepository(storage.postgresql(), logger, debug);
            case "mongodb" -> new MongoCreditsRepository(storage.mongodb());
            case "sqlite" -> new SqliteCreditsRepository(resolvePath(storage.sqlite().file()), logger, debug);
            case "h2" -> new H2CreditsRepository(resolvePath(storage.h2().file()), logger, debug);
            default -> new H2CreditsRepository(resolvePath(storage.h2().file()), logger, debug);
        };
    }

    public static CreditsRepository createH2Fallback(CreditConfig.StorageSettings storage, Logger logger, boolean debug) {
        return new H2CreditsRepository(resolvePath(storage.h2().file()), logger, debug);
    }

    private static String resolvePath(String filePath) {
        return Path.of(filePath).toAbsolutePath().toString();
    }
}
