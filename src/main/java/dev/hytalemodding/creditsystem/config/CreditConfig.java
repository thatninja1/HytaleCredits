package dev.hytalemodding.creditsystem.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public record CreditConfig(
        UiSettings ui,
        String currencyName,
        List<CategoryEntry> categories,
        DatabaseSettings database,
        boolean debug
) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static CreditConfig loadDefault(Logger logger) throws IOException {
        Path dataDir = Path.of("plugins", "CreditSystem");
        Files.createDirectories(dataDir);

        Path configPath = dataDir.resolve("config.json");
        if (!Files.exists(configPath)) {
            CreditConfig defaults = defaults();
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(defaults, writer);
            }
            logger.info("Wrote default config.json to " + configPath.toAbsolutePath());
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            CreditConfig loaded = GSON.fromJson(reader, CreditConfig.class);
            return sanitize(loaded);
        }
    }

    private static CreditConfig sanitize(CreditConfig value) {
        CreditConfig defaults = defaults();
        if (value == null) {
            return defaults;
        }

        UiSettings ui = value.ui() == null ? defaults.ui() : new UiSettings(
                value.ui().title() == null || value.ui().title().isBlank() ? defaults.ui().title() : value.ui().title()
        );

        String currencyName = value.currencyName() == null || value.currencyName().isBlank()
                ? defaults.currencyName()
                : value.currencyName();

        List<CategoryEntry> categories = value.categories() == null || value.categories().isEmpty()
                ? defaults.categories()
                : value.categories().stream()
                .filter(Objects::nonNull)
                .map(c -> new CategoryEntry(
                        c.key() == null || c.key().isBlank() ? "category" : c.key(),
                        c.name() == null || c.name().isBlank() ? "Category" : c.name()
                ))
                .toList();

        DatabaseSettings database = value.database() == null ? defaults.database() : value.database().sanitize();

        return new CreditConfig(ui, currencyName, categories, database, value.debug());
    }

    public static CreditConfig defaults() {
        List<CategoryEntry> categories = new ArrayList<>();
        categories.add(new CategoryEntry("vip", "VIP"));
        categories.add(new CategoryEntry("tags", "Tags"));

        return new CreditConfig(
                new UiSettings("Credit Shop"),
                "Credits",
                categories,
                DatabaseSettings.defaults(),
                false
        );
    }

    public record UiSettings(String title) {
    }

    public record CategoryEntry(String key, String name) {
    }

    public record DatabaseSettings(
            String host,
            int port,
            String database,
            String username,
            String password,
            int maximumPoolSize,
            int connectTimeoutMs,
            int socketTimeoutMs
    ) {
        public static DatabaseSettings defaults() {
            return new DatabaseSettings("127.0.0.1", 3306, "hytale", "root", "password", 10, 5000, 15000);
        }

        public DatabaseSettings sanitize() {
            DatabaseSettings defaults = defaults();
            return new DatabaseSettings(
                    host == null || host.isBlank() ? defaults.host : host,
                    port <= 0 ? defaults.port : port,
                    database == null || database.isBlank() ? defaults.database : database,
                    username == null || username.isBlank() ? defaults.username : username,
                    password == null ? defaults.password : password,
                    maximumPoolSize <= 0 ? defaults.maximumPoolSize : maximumPoolSize,
                    connectTimeoutMs <= 0 ? defaults.connectTimeoutMs : connectTimeoutMs,
                    socketTimeoutMs <= 0 ? defaults.socketTimeoutMs : socketTimeoutMs
            );
        }
    }
}
