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
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

public record CreditConfig(
        UiSettings ui,
        String currencyName,
        List<CategoryEntry> categories,
        StorageSettings storage,
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

        StorageSettings storage = value.storage() == null ? defaults.storage() : value.storage().sanitize();
        return new CreditConfig(ui, currencyName, categories, storage, value.debug());
    }

    public static CreditConfig defaults() {
        List<CategoryEntry> categories = new ArrayList<>();
        categories.add(new CategoryEntry("vip", "VIP"));
        categories.add(new CategoryEntry("tags", "Tags"));

        return new CreditConfig(
                new UiSettings("Credit Shop"),
                "Credits",
                categories,
                StorageSettings.defaults(),
                false
        );
    }

    public record UiSettings(String title) {
    }

    public record CategoryEntry(String key, String name) {
    }

    public record StorageSettings(
            String type,
            H2Settings h2,
            SqliteSettings sqlite,
            MysqlSettings mysql,
            MysqlSettings mariadb,
            PostgreSqlSettings postgresql,
            MongoDbSettings mongodb
    ) {
        public static StorageSettings defaults() {
            return new StorageSettings(
                    "h2",
                    new H2Settings("plugins/CreditSystem/credits"),
                    new SqliteSettings("plugins/CreditSystem/credits.db"),
                    new MysqlSettings("127.0.0.1", 3306, "creditsystem", "root", "password", false),
                    new MysqlSettings("127.0.0.1", 3306, "creditsystem", "root", "password", false),
                    new PostgreSqlSettings("127.0.0.1", 5432, "creditsystem", "postgres", "password", false),
                    new MongoDbSettings("mongodb://user:pass@127.0.0.1:27017", "creditsystem")
            );
        }

        public StorageSettings sanitize() {
            StorageSettings defaults = defaults();
            String normalized = type == null ? defaults.type : type.toLowerCase(Locale.ROOT).trim();
            if (normalized.isBlank()) {
                normalized = defaults.type;
            }
            return new StorageSettings(
                    normalized,
                    h2 == null ? defaults.h2 : h2.sanitize(defaults.h2),
                    sqlite == null ? defaults.sqlite : sqlite.sanitize(defaults.sqlite),
                    mysql == null ? defaults.mysql : mysql.sanitize(defaults.mysql),
                    mariadb == null ? defaults.mariadb : mariadb.sanitize(defaults.mariadb),
                    postgresql == null ? defaults.postgresql : postgresql.sanitize(defaults.postgresql),
                    mongodb == null ? defaults.mongodb : mongodb.sanitize(defaults.mongodb)
            );
        }
    }

    public record H2Settings(String file) {
        public H2Settings sanitize(H2Settings defaults) {
            return new H2Settings(file == null || file.isBlank() ? defaults.file : file);
        }
    }

    public record SqliteSettings(String file) {
        public SqliteSettings sanitize(SqliteSettings defaults) {
            return new SqliteSettings(file == null || file.isBlank() ? defaults.file : file);
        }
    }

    public record MysqlSettings(String host, int port, String database, String username, String password, boolean useSSL) {
        public MysqlSettings sanitize(MysqlSettings defaults) {
            return new MysqlSettings(
                    host == null || host.isBlank() ? defaults.host : host,
                    port <= 0 ? defaults.port : port,
                    database == null || database.isBlank() ? defaults.database : database,
                    username == null || username.isBlank() ? defaults.username : username,
                    password == null ? defaults.password : password,
                    useSSL
            );
        }
    }

    public record PostgreSqlSettings(String host, int port, String database, String username, String password, boolean ssl) {
        public PostgreSqlSettings sanitize(PostgreSqlSettings defaults) {
            return new PostgreSqlSettings(
                    host == null || host.isBlank() ? defaults.host : host,
                    port <= 0 ? defaults.port : port,
                    database == null || database.isBlank() ? defaults.database : database,
                    username == null || username.isBlank() ? defaults.username : username,
                    password == null ? defaults.password : password,
                    ssl
            );
        }
    }

    public record MongoDbSettings(String uri, String database) {
        public MongoDbSettings sanitize(MongoDbSettings defaults) {
            return new MongoDbSettings(
                    uri == null || uri.isBlank() ? defaults.uri : uri,
                    database == null || database.isBlank() ? defaults.database : database
            );
        }
    }
}
