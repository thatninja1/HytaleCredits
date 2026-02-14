package dev.hytalemodding.creditsystem.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public abstract class JdbcCreditsRepository implements CreditsRepository {
    private final String backendName;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String driverClass;
    private final Logger logger;
    private final boolean debug;
    private final AtomicBoolean ensuredSchema = new AtomicBoolean(false);

    protected JdbcCreditsRepository(
            String backendName,
            String jdbcUrl,
            String username,
            String password,
            String driverClass,
            Logger logger,
            boolean debug
    ) {
        this.backendName = backendName;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.driverClass = driverClass;
        this.logger = logger;
        this.debug = debug;
    }

    @Override
    public void initialize() {
        executeStatement(createTableSql());
        ensuredSchema.set(true);
    }

    @Override
    public long getBalance(UUID uuid, String name) {
        try (Connection connection = openConnection()) {
            ensureRow(connection, uuid, name);
            try (PreparedStatement statement = connection.prepareStatement("SELECT balance FROM credits WHERE player_uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getLong("balance");
                    }
                }
            }
            return 0L;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to fetch balance", e);
        }
    }

    @Override
    public void addCredits(UUID uuid, String name, long amount) {
        if (amount <= 0) {
            return;
        }
        executeThreeArg(addSql(), uuid, name, amount);
    }

    @Override
    public void removeCredits(UUID uuid, String name, long amount) {
        if (amount <= 0) {
            return;
        }
        executeThreeArg(removeSql(), uuid, name, amount);
    }

    @Override
    public void setCredits(UUID uuid, String name, long amount) {
        executeThreeArg(setSql(), uuid, name, Math.max(0L, amount));
    }

    @Override
    public boolean tryPurchase(UUID uuid, String name, long price) {
        if (price <= 0) {
            return true;
        }

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(purchaseSql())) {
            ensureRow(connection, uuid, name);
            statement.setString(1, name);
            statement.setLong(2, price);
            statement.setString(3, uuid.toString());
            statement.setLong(4, price);
            int updated = statement.executeUpdate();
            return updated > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to execute purchase", e);
        }
    }

    @Override
    public void shutdown() {
        // DriverManager connection handling, nothing to close.
    }

    @Override
    public String backendName() {
        return backendName;
    }

    @Override
    public String location() {
        return jdbcUrl;
    }

    protected abstract String createTableSql();

    protected abstract String ensureSql();

    protected abstract String addSql();

    protected abstract String removeSql();

    protected abstract String setSql();

    protected abstract String purchaseSql();

    private void ensureRow(Connection connection, UUID uuid, String name) {
        try {
            if (ensuredSchema.compareAndSet(false, true)) {
                executeStatement(createTableSql());
            }
            try (PreparedStatement statement = connection.prepareStatement(ensureSql())) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to ensure credits row", e);
        }
    }

    private void executeThreeArg(String sql, UUID uuid, String name, long amount) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setLong(3, amount);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to execute credits SQL", e);
        }
    }

    private void executeStatement(String sql) {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize credits schema", e);
        }
    }

    protected Connection openConnection() throws SQLException {
        try {
            if (driverClass != null && !driverClass.isBlank()) {
                Class.forName(driverClass);
            }
        } catch (ClassNotFoundException e) {
            throw new SQLException("Database driver class not found: " + driverClass, e);
        }

        if (debug) {
            logger.info("Opening SQL connection to " + jdbcUrl);
        }

        if (username == null || username.isBlank()) {
            return DriverManager.getConnection(jdbcUrl);
        }

        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password == null ? "" : password);
        return DriverManager.getConnection(jdbcUrl, props);
    }
}
