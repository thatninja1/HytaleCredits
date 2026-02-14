package dev.hytalemodding.creditsystem.db;

import java.util.logging.Logger;

public final class SqliteCreditsRepository extends JdbcCreditsRepository {
    public SqliteCreditsRepository(String filePath, Logger logger, boolean debug) {
        super("sqlite", "jdbc:sqlite:" + filePath, null, null, "org.sqlite.JDBC", logger, debug);
    }

    @Override
    protected String createTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS credits (
                    player_uuid TEXT PRIMARY KEY,
                    player_name TEXT,
                    balance BIGINT NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL DEFAULT (strftime('%s','now'))
                )
                """;
    }

    @Override
    protected String ensureSql() {
        return """
                INSERT INTO credits (player_uuid, player_name, balance, updated_at)
                VALUES (?, ?, 0, strftime('%s','now'))
                ON CONFLICT(player_uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    updated_at = strftime('%s','now')
                """;
    }

    @Override
    protected String addSql() {
        return """
                INSERT INTO credits (player_uuid, player_name, balance, updated_at)
                VALUES (?, ?, ?, strftime('%s','now'))
                ON CONFLICT(player_uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    balance = credits.balance + excluded.balance,
                    updated_at = strftime('%s','now')
                """;
    }

    @Override
    protected String removeSql() {
        return """
                INSERT INTO credits (player_uuid, player_name, balance, updated_at)
                VALUES (?, ?, 0, strftime('%s','now'))
                ON CONFLICT(player_uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    balance = MAX(credits.balance - excluded.balance, 0),
                    updated_at = strftime('%s','now')
                """;
    }

    @Override
    protected String setSql() {
        return """
                INSERT INTO credits (player_uuid, player_name, balance, updated_at)
                VALUES (?, ?, ?, strftime('%s','now'))
                ON CONFLICT(player_uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    balance = MAX(excluded.balance, 0),
                    updated_at = strftime('%s','now')
                """;
    }
    @Override
    protected String purchaseSql() {
        return """
                UPDATE credits
                SET player_name = ?,
                    balance = balance - ?,
                    updated_at = strftime('%s','now')
                WHERE player_uuid = ? AND balance >= ?
                """;
    }

}
