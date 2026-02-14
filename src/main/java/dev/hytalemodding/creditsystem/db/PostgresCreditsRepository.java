package dev.hytalemodding.creditsystem.db;

import dev.hytalemodding.creditsystem.config.CreditConfig;

import java.util.logging.Logger;

public final class PostgresCreditsRepository extends JdbcCreditsRepository {
    public PostgresCreditsRepository(CreditConfig.PostgreSqlSettings settings, Logger logger, boolean debug) {
        super(
                "postgresql",
                "jdbc:postgresql://" + settings.host() + ":" + settings.port() + "/" + settings.database() + "?ssl=" + settings.ssl(),
                settings.username(),
                settings.password(),
                "org.postgresql.Driver",
                logger,
                debug
        );
    }

    @Override
    protected String createTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS credits (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(32),
                    balance BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
    }

    @Override
    protected String ensureSql() {
        return """
                INSERT INTO credits (player_uuid, player_name, balance, updated_at)
                VALUES (?, ?, 0, CURRENT_TIMESTAMP)
                ON CONFLICT (player_uuid) DO UPDATE SET
                    player_name = EXCLUDED.player_name,
                    updated_at = CURRENT_TIMESTAMP
                """;
    }

    @Override
    protected String addSql() {
        return """
                INSERT INTO credits (player_uuid, player_name, balance, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (player_uuid) DO UPDATE SET
                    player_name = EXCLUDED.player_name,
                    balance = credits.balance + EXCLUDED.balance,
                    updated_at = CURRENT_TIMESTAMP
                """;
    }

    @Override
    protected String removeSql() {
        return """
                INSERT INTO credits (player_uuid, player_name, balance, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (player_uuid) DO UPDATE SET
                    player_name = EXCLUDED.player_name,
                    balance = GREATEST(credits.balance - EXCLUDED.balance, 0),
                    updated_at = CURRENT_TIMESTAMP
                """;
    }

    @Override
    protected String setSql() {
        return """
                INSERT INTO credits (player_uuid, player_name, balance, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (player_uuid) DO UPDATE SET
                    player_name = EXCLUDED.player_name,
                    balance = GREATEST(EXCLUDED.balance, 0),
                    updated_at = CURRENT_TIMESTAMP
                """;
    }
    @Override
    protected String purchaseSql() {
        return """
                UPDATE credits
                SET player_name = ?,
                    balance = balance - ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE player_uuid = ? AND balance >= ?
                """;
    }

}
