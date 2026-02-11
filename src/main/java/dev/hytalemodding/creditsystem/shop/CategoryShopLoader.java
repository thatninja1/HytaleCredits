package dev.hytalemodding.creditsystem.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

    public CategoryShopLoader(Logger logger) {
        this.logger = logger;
        this.shopDirectory = Path.of("plugins", "CreditSystem", "shops");
        this.shopCache = new LinkedHashMap<>();
    }

    public synchronized Path ensureCategoryFile(String categoryKey) {
        try {
            Files.createDirectories(shopDirectory);
            Path file = shopDirectory.resolve(categoryKey + ".json");
            if (!Files.exists(file)) {
                JsonObject root = new JsonObject();

                JsonObject item1 = new JsonObject();
                item1.addProperty("name", "VIP Rank");
                item1.addProperty("price", 1000);
                item1.addProperty("description", "Unlocks VIP permissions and chat prefix.");
                JsonArray item1Commands = new JsonArray();
                item1Commands.add("lp user {player} parent add vip");
                item1Commands.add("say {player} purchased VIP!");
                item1.add("commands", item1Commands);
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
            Map<String, ShopItem> parsed = parseShopFile(file);
            Map<String, ShopItem> immutableOrdered = Collections.unmodifiableMap(new LinkedHashMap<>(parsed));
            shopCache.put(normalized, immutableOrdered);
            return immutableOrdered;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to parse shop file for " + normalized + ": " + file.toAbsolutePath(), e);
            if (cached != null) {
                return cached;
            }
            throw new IllegalStateException("Failed to parse shop file for " + normalized + ": " + file.toAbsolutePath(), e);
        }
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
                Map<String, ShopItem> parsed = parseShopFile(file);
                shopCache.put(key, Collections.unmodifiableMap(new LinkedHashMap<>(parsed)));
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

    private Map<String, ShopItem> parseShopFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            List<OrderedItem> numeric = new ArrayList<>();
            List<OrderedItem> nonNumeric = new ArrayList<>();
            int originalIndex = 0;

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    originalIndex++;
                    continue;
                }

                String itemId = entry.getKey();
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

                ShopItem shopItem = new ShopItem(name, Math.max(0L, price), description, commands);
                Matcher matcher = ITEM_NUMERIC_KEY.matcher(itemId);
                if (matcher.matches()) {
                    int numericSuffix = Integer.parseInt(matcher.group(1));
                    numeric.add(new OrderedItem(itemId, shopItem, originalIndex, numericSuffix));
                } else {
                    nonNumeric.add(new OrderedItem(itemId, shopItem, originalIndex, Integer.MAX_VALUE));
                }
                originalIndex++;
            }

            numeric.sort((a, b) -> {
                int byNumber = Integer.compare(a.numericSuffix(), b.numericSuffix());
                if (byNumber != 0) {
                    return byNumber;
                }
                return Integer.compare(a.originalIndex(), b.originalIndex());
            });
            nonNumeric.sort((a, b) -> Integer.compare(a.originalIndex(), b.originalIndex()));

            Map<String, ShopItem> ordered = new LinkedHashMap<>();
            for (OrderedItem item : numeric) {
                ordered.put(item.itemId(), item.shopItem());
            }
            for (OrderedItem item : nonNumeric) {
                ordered.put(item.itemId(), item.shopItem());
            }
            return ordered;
        }
    }

    private String normalizeKey(String key) {
        return Objects.requireNonNullElse(key, "").toLowerCase();
    }

    private record OrderedItem(String itemId, ShopItem shopItem, int originalIndex, int numericSuffix) {
    }

    public record ReloadReport(int successfulFiles, Map<String, String> failures) {
    }
}
