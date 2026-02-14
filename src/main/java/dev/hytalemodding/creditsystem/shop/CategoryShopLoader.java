package dev.hytalemodding.creditsystem.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hytalemodding.creditsystem.config.CreditConfig;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CategoryShopLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern ITEM_NUMERIC_KEY = Pattern.compile("^item(\\d+)$", Pattern.CASE_INSENSITIVE);

    private final Path shopDirectory;
    private final Logger logger;
    private final Map<String, Map<String, ShopItem>> shopCache;
    private final Map<String, CategoryStyles> styleCache;

    public CategoryShopLoader(Logger logger) {
        this.logger = logger;
        this.shopDirectory = Path.of("plugins", "CreditSystem", "shops");
        this.shopCache = new LinkedHashMap<>();
        this.styleCache = new LinkedHashMap<>();
    }

    public synchronized Path ensureCategoryFile(String categoryKey) {
        try {
            Files.createDirectories(shopDirectory);
            Path file = shopDirectory.resolve(categoryKey + ".json");
            if (!Files.exists(file)) {
                JsonObject root = new JsonObject();

                JsonObject meta = new JsonObject();
                JsonObject metaStyles = new JsonObject();
                JsonObject metaDescriptionStyle = new JsonObject();
                metaDescriptionStyle.addProperty("color", "#808080");
                metaDescriptionStyle.addProperty("fontSize", 13);
                metaStyles.add("itemDescription", metaDescriptionStyle);
                meta.add("styles", metaStyles);
                root.add("meta", meta);

                JsonObject item1 = new JsonObject();
                item1.addProperty("name", "VIP Rank");
                item1.addProperty("price", 1000);
                item1.addProperty("description", "Unlocks VIP permissions, chat prefix, extra homes, kit preview commands, and bonus quality-of-life store perks.");
                JsonArray item1Commands = new JsonArray();
                item1Commands.add("lp user {player} parent add vip");
                item1Commands.add("say {player} purchased VIP!");
                item1.add("commands", item1Commands);
                JsonObject item1Styles = new JsonObject();
                JsonObject item1NameStyle = new JsonObject();
                item1NameStyle.addProperty("color", "#F8FAFC");
                item1NameStyle.addProperty("fontSize", 20);
                item1Styles.add("name", item1NameStyle);
                item1.add("styles", item1Styles);
                root.add("item1", item1);

                JsonObject item2 = new JsonObject();
                item2.addProperty("name", "Example Tag");
                item2.addProperty("price", 250);
                item2.addProperty("description", "A sample cosmetic tag for testing purchases.");
                item2.addProperty("command", "say {player} bought a tag!");
                root.add("item2", item2);

                try (Writer writer = Files.newBufferedWriter(file)) {
                    GSON.toJson(root, writer);
                }
                logger.info("[CreditSystem] Created shop file: " + file.toAbsolutePath());
            }
            return file;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to ensure shop file for " + categoryKey, e);
        }
    }

    public synchronized Map<String, ShopItem> loadCategoryItems(String categoryKey) {
        String normalized = normalizeKey(categoryKey);
        Map<String, ShopItem> cached = shopCache.get(normalized);
        if (cached != null) {
            return cached;
        }

        Path file = ensureCategoryFile(normalized);
        logger.info("[CreditSystem] Loading shop file for category=" + normalized + " path=" + file.toAbsolutePath());

        try {
            ParsedCategory parsed = parseShopFile(file);
            Map<String, ShopItem> immutableOrdered = Collections.unmodifiableMap(new LinkedHashMap<>(parsed.items()));
            shopCache.put(normalized, immutableOrdered);
            styleCache.put(normalized, parsed.styles());
            return immutableOrdered;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to parse shop file for " + normalized + ": " + file.toAbsolutePath(), e);
            if (cached != null) {
                return cached;
            }
            throw new IllegalStateException("Failed to parse shop file for " + normalized + ": " + file.toAbsolutePath(), e);
        }
    }

    public synchronized CategoryStyles loadCategoryStyles(String categoryKey) {
        String normalized = normalizeKey(categoryKey);
        CategoryStyles cached = styleCache.get(normalized);
        if (cached != null) {
            return cached;
        }
        loadCategoryItems(normalized);
        return styleCache.getOrDefault(normalized, CategoryStyles.empty());
    }

    public synchronized ReloadReport reloadAllShops(Collection<String> categoryKeys) {
        int successCount = 0;
        Map<String, String> failures = new LinkedHashMap<>();

        for (String rawKey : categoryKeys) {
            if (rawKey == null || rawKey.isBlank()) {
                continue;
            }

            String key = normalizeKey(rawKey);
            Path file = ensureCategoryFile(key);
            try {
                ParsedCategory parsed = parseShopFile(file);
                shopCache.put(key, Collections.unmodifiableMap(new LinkedHashMap<>(parsed.items())));
                styleCache.put(key, parsed.styles());
                successCount++;
            } catch (Exception parseError) {
                String reason = parseError.getMessage() == null ? "unknown parse error" : parseError.getMessage();
                failures.put(file.toAbsolutePath().toString(), reason);
                logger.log(Level.WARNING, "[CreditSystem] Shop reload failed for " + file.toAbsolutePath()
                        + ". Keeping previous cached version if available.", parseError);
            }
        }

        return new ReloadReport(successCount, failures);
    }

    private ParsedCategory parseShopFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<OrderedItem> numeric = new ArrayList<>();

            CategoryStyles categoryStyles = parseMetaStyles(root);
            int originalIndex = 0;

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                String itemId = entry.getKey();
                Matcher matcher = ITEM_NUMERIC_KEY.matcher(itemId);
                if (!matcher.matches() || !entry.getValue().isJsonObject()) {
                    originalIndex++;
                    continue;
                }

                JsonObject obj = entry.getValue().getAsJsonObject();
                String name = obj.has("name") ? obj.get("name").getAsString() : itemId;
                long price = obj.has("price") ? obj.get("price").getAsLong() : 0L;
                String description = obj.has("description") ? obj.get("description").getAsString() : "";

                List<String> commands = new ArrayList<>();
                if (obj.has("commands") && obj.get("commands").isJsonArray()) {
                    for (JsonElement cmd : obj.getAsJsonArray("commands")) {
                        if (cmd.isJsonPrimitive()) {
                            commands.add(cmd.getAsString());
                        }
                    }
                } else if (obj.has("command") && obj.get("command").isJsonPrimitive()) {
                    commands.add(obj.get("command").getAsString());
                }

                ShopItem.ItemStyles itemStyles = parseItemStyles(obj, file, itemId);
                ShopItem shopItem = new ShopItem(name, Math.max(0L, price), description, commands, itemStyles);
                int numericSuffix = Integer.parseInt(matcher.group(1));
                numeric.add(new OrderedItem(itemId, shopItem, originalIndex, numericSuffix));
                originalIndex++;
            }

            numeric.sort((a, b) -> {
                int byNumber = Integer.compare(a.numericSuffix(), b.numericSuffix());
                if (byNumber != 0) {
                    return byNumber;
                }
                return Integer.compare(a.originalIndex(), b.originalIndex());
            });

            Map<String, ShopItem> ordered = new LinkedHashMap<>();
            for (OrderedItem item : numeric) {
                ordered.put(item.itemId(), item.shopItem());
            }
            return new ParsedCategory(ordered, categoryStyles);
        }
    }

    private CategoryStyles parseMetaStyles(JsonObject root) {
        if (!root.has("meta") || !root.get("meta").isJsonObject()) {
            return CategoryStyles.empty();
        }
        JsonObject meta = root.getAsJsonObject("meta");
        if (!meta.has("styles") || !meta.get("styles").isJsonObject()) {
            return CategoryStyles.empty();
        }
        JsonObject styles = meta.getAsJsonObject("styles");
        return new CategoryStyles(
                parseTextStyle(styles, "itemName", "meta.styles.itemName"),
                parseTextStyle(styles, "itemPrice", "meta.styles.itemPrice"),
                parseTextStyle(styles, "itemDescription", "meta.styles.itemDescription"),
                parseTextStyle(styles, "buyLabel", "meta.styles.buyLabel")
        );
    }

    private ShopItem.ItemStyles parseItemStyles(JsonObject obj, Path file, String itemId) {
        if (!obj.has("styles") || !obj.get("styles").isJsonObject()) {
            return null;
        }
        JsonObject styles = obj.getAsJsonObject("styles");
        String basePath = file.getFileName() + ":" + itemId + ".styles";
        return new ShopItem.ItemStyles(
                parseTextStyle(styles, "name", basePath + ".name"),
                parseTextStyle(styles, "price", basePath + ".price"),
                parseTextStyle(styles, "description", basePath + ".description"),
                parseTextStyle(styles, "buy", basePath + ".buy")
        );
    }

    private CreditConfig.TextStyle parseTextStyle(JsonObject parent, String key, String path) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) {
            return null;
        }
        JsonObject value = parent.getAsJsonObject(key);
        CreditConfig.TextStyle base = new CreditConfig.TextStyle("#FFFFFF", 14);

        String color = base.color();
        if (value.has("color") && value.get("color").isJsonPrimitive()) {
            String raw = value.get("color").getAsString();
            if (raw.matches("^#[0-9A-Fa-f]{6}$")) {
                color = raw;
            } else {
                logger.warning("[CreditSystem] Invalid color for " + path + ".color=" + raw
                        + ". Expected #RRGGBB. Ignoring override.");
            }
        }

        int fontSize = base.fontSize();
        if (value.has("fontSize") && value.get("fontSize").isJsonPrimitive()) {
            int raw = value.get("fontSize").getAsInt();
            if (raw >= 8 && raw <= 72) {
                fontSize = raw;
            } else {
                logger.warning("[CreditSystem] Invalid fontSize for " + path + ".fontSize=" + raw
                        + ". Allowed range is 8..72. Ignoring override.");
            }
        }

        return new CreditConfig.TextStyle(color, fontSize);
    }

    private String normalizeKey(String key) {
        return Objects.requireNonNullElse(key, "").toLowerCase();
    }

    private record OrderedItem(String itemId, ShopItem shopItem, int originalIndex, int numericSuffix) {
    }

    private record ParsedCategory(Map<String, ShopItem> items, CategoryStyles styles) {
    }

    public record CategoryStyles(
            CreditConfig.TextStyle itemName,
            CreditConfig.TextStyle itemPrice,
            CreditConfig.TextStyle itemDescription,
            CreditConfig.TextStyle buyLabel
    ) {
        public static CategoryStyles empty() {
            return new CategoryStyles(null, null, null, null);
        }
    }

    public record ReloadReport(int successfulFiles, Map<String, String> failures) {
    }
}
