package dev.hytalemodding.creditsystem.db;

import dev.hytalemodding.creditsystem.config.CreditConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

public final class SqlCreditsRepository {
    private final CreditConfig.DatabaseSettings databaseSettings;
    private final Logger logger;
    private final boolean debug;
    private final String jdbcUrl;

    public SqlCreditsRepository(CreditConfig.DatabaseSettings databaseSettings, Logger logger, boolean debug) {
        this.databaseSettings = databaseSettings;
        this.logger = logger;
        this.debug = debug;
        this.jdbcUrl = "jdbc:mysql://" + databaseSettings.host() + ":" + databaseSettings.port() + "/"
                + databaseSettings.database() + "?useSSL=false&allowPublicKeyRetrieval=true";
    }

    public void initialize() throws SQLException {
        executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_credits (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(32) NOT NULL,
                    balance BIGINT NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    public long getBalance(UUID playerUuid, String playerName) throws SQLException {
        ensureRow(playerUuid, playerName);
        String sql = "SELECT balance FROM player_credits WHERE player_uuid = ?";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("balance");
                }
            }
        }

        return 0L;
    }

    public void addCredits(UUID playerUuid, String playerName, long amount) throws SQLException {
        if (amount <= 0) {
            return;
        }

        String sql = """
                INSERT INTO player_credits (player_uuid, player_name, balance)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name),
                    balance = balance + VALUES(balance)
                """;

        executePrepared(sql, playerUuid, playerName, amount);
    }

    public void removeCredits(UUID playerUuid, String playerName, long amount) throws SQLException {
        if (amount <= 0) {
            return;
        }

        String sql = """
                INSERT INTO player_credits (player_uuid, player_name, balance)
                VALUES (?, ?, 0)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name),
                    balance = GREATEST(balance - ?, 0)
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, playerName);
            statement.setLong(3, amount);
            statement.executeUpdate();
        }
    }

    public void setCredits(UUID playerUuid, String playerName, long amount) throws SQLException {
        long clamped = Math.max(0L, amount);
        String sql = """
                INSERT INTO player_credits (player_uuid, player_name, balance)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name),
                    balance = VALUES(balance)
                """;

        executePrepared(sql, playerUuid, playerName, clamped);
    }

    public void close() {
        // DriverManager-based implementation has no pooled resource to close.
    }

    private void ensureRow(UUID playerUuid, String playerName) throws SQLException {
        String sql = """
                INSERT INTO player_credits (player_uuid, player_name, balance)
                VALUES (?, ?, 0)
                ON DUPLICATE KEY UPDATE player_name = VALUES(player_name)
                """;

        executePrepared(sql, playerUuid, playerName, 0L);
    }

    private void executePrepared(String sql, UUID playerUuid, String playerName, long amount) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, playerName);
            statement.setLong(3, amount);
            statement.executeUpdate();
        }
    }

    private void executeUpdate(String sql) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private Connection openConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", databaseSettings.username());
        props.setProperty("password", databaseSettings.password());
        props.setProperty("connectTimeout", Integer.toString(databaseSettings.connectTimeoutMs()));
        props.setProperty("socketTimeout", Integer.toString(databaseSettings.socketTimeoutMs()));

        if (debug) {
            logger.info("Opening SQL connection to " + jdbcUrl);
        }

        return DriverManager.getConnection(jdbcUrl, props);
    }
}
