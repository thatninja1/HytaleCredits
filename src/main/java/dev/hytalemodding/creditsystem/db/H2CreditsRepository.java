package dev.hytalemodding.creditsystem.db;

import java.util.logging.Logger;

public final class H2CreditsRepository extends JdbcCreditsRepository {
    public H2CreditsRepository(String filePath, Logger logger, boolean debug) {
        super(
                "h2",
                "jdbc:h2:file:" + filePath + ";AUTO_SERVER=TRUE;MODE=MySQL",
                "sa",
                "",
                "org.h2.Driver",
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
                ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), updated_at = CURRENT_TIMESTAMP
                """;
    }

    @Override
    protected String addSql() {
        return """
                INSERT INTO credits (player_uuid, player_name, balance, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name),
                    balance = balance + VALUES(balance),
                    updated_at = CURRENT_TIMESTAMP
                """;
    }

    @Override
    protected String removeSql() {
        return """
                INSERT INTO credits (player_uuid, player_name, balance, updated_at)
                VALUES (?, ?, 0, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name),
                    balance = GREATEST(balance - ?, 0),
                    updated_at = CURRENT_TIMESTAMP
                """;
    }

    @Override
    protected String setSql() {
        return """
                INSERT INTO credits (player_uuid, player_name, balance, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name),
                    balance = GREATEST(VALUES(balance), 0),
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
