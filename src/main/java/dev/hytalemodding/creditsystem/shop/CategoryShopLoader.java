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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class CategoryShopLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path shopDirectory;
    private final Logger logger;

    public CategoryShopLoader(Logger logger) {
        this.logger = logger;
        this.shopDirectory = Path.of("plugins", "CreditSystem", "shops");
    }

    public Path ensureCategoryFile(String categoryKey) {
        try {
            Files.createDirectories(shopDirectory);
            Path file = shopDirectory.resolve(categoryKey + ".json");
            if (!Files.exists(file)) {
                JsonObject root = new JsonObject();
                JsonObject item1 = new JsonObject();
                item1.addProperty("name", "VIP Rank");
                item1.addProperty("price", 1000);
                item1.addProperty("command", "lp user {player} parent add vip");
                root.add("item1", item1);

                JsonObject item2 = new JsonObject();
                item2.addProperty("name", "Example Tag");
                item2.addProperty("price", 250);
                JsonArray commands = new JsonArray();
                commands.add("say {player} bought a tag!");
                item2.add("commands", commands);
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

    public Map<String, ShopItem> loadCategoryItems(String categoryKey) {
        Path file = ensureCategoryFile(categoryKey);
        logger.info("[CreditSystem] Loading shop file for category=" + categoryKey + " path=" + file.toAbsolutePath());

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, ShopItem> items = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }

                JsonObject obj = entry.getValue().getAsJsonObject();
                String name = obj.has("name") ? obj.get("name").getAsString() : entry.getKey();
                long price = obj.has("price") ? obj.get("price").getAsLong() : 0L;

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

                items.put(entry.getKey(), new ShopItem(name, Math.max(0L, price), commands));
            }
            return items;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse shop file for " + categoryKey + ": " + file.toAbsolutePath(), e);
        }
    }

    public record ShopItem(String name, long price, List<String> commands) {
    }
}
