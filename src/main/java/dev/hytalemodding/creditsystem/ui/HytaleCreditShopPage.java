package dev.hytalemodding.creditsystem.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.creditsystem.config.CreditConfig;
import dev.hytalemodding.creditsystem.service.CreditsService;
import dev.hytalemodding.creditsystem.shop.CategoryShopLoader;
import dev.hytalemodding.creditsystem.shop.ShopItem;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HytaleCreditShopPage extends CustomUIPage {
    private static final String UI_TEMPLATE = "Pages/Credits/CreditShop.ui";
    private static final String UI_RESOURCE_PATH = "Common/UI/Custom/Pages/Credits/CreditShop.ui";
    private static final int MAX_CATEGORY_BUTTONS = 12;
    private static final int MAX_ITEM_ROWS = 30;

    private static final String SCROLL_CONTAINER_VISIBLE_ANCHOR = "(Top: 14, Left: 10, Width: 960, Height: 500)";
    private static final String SCROLL_CONTAINER_HIDDEN_ANCHOR = "(Top: 0, Left: 0, Width: 0, Height: 0)";

    private final CreditsService creditsService;
    private final CreditConfig config;
    private final Logger logger;
    private final CategoryShopLoader shopLoader;

    private String selectedCategoryKey;
    private String selectedCategoryName;

    public HytaleCreditShopPage(PlayerRef playerRef, CreditsService creditsService, CreditConfig config, Logger logger) {
        super(playerRef, CustomPageLifetime.CanDismiss);
        this.creditsService = creditsService;
        this.config = config;
        this.logger = logger;
        this.shopLoader = new CategoryShopLoader(logger);

        for (CreditConfig.CategoryEntry category : config.categories()) {
            shopLoader.ensureCategoryFile(category.key());
        }
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder uiCommandBuilder, UIEventBuilder uiEventBuilder, Store<EntityStore> store) {
        if (!validateUiMarkupSafely()) {
            logger.severe("[CreditSystem] Credit shop UI validation failed; refusing to open page.");
            playerRef.sendMessage(Message.raw("Failed to open Credit Shop UI (invalid UI markup)."));
            close();
            return;
        }

        try (InputStream stream = getClass().getResourceAsStream("/" + UI_RESOURCE_PATH)) {
            if (stream == null) {
                logger.severe("[CreditSystem] UI resource missing: " + UI_RESOURCE_PATH);
                playerRef.sendMessage(Message.raw("Failed to open Credit Shop UI (resource missing)."));
                close();
                return;
            }
        } catch (Exception resourceException) {
            logger.log(Level.SEVERE, "[CreditSystem] Error checking UI resource", resourceException);
            playerRef.sendMessage(Message.raw("Failed to open Credit Shop UI (resource check error)."));
            close();
            return;
        }

        logger.info("[CreditSystem] Credit shop UI resource exists: " + UI_RESOURCE_PATH);
        uiCommandBuilder.append(UI_TEMPLATE);
        logger.info("[CreditSystem] Credit shop UI append succeeded for " + playerRef.getUsername());

        uiCommandBuilder.set("#TitleLabel.Text", config.ui().title());
        if (!creditsService.isOnline()) {
            uiCommandBuilder.set("#CreditsBalanceLabel.Text", "Credits system unavailable");
        } else {
            long balance = creditsService.getBalance(playerRef.getUuid(), playerRef.getUsername());
            uiCommandBuilder.set("#CreditsBalanceLabel.Text", config.currencyName() + ": " + balance);
        }

        uiCommandBuilder.set("#SelectedCategoryLabel.Text", selectedCategoryName == null ? "" : selectedCategoryName);

        List<CreditConfig.CategoryEntry> categories = config.categories();
        for (int i = 0; i < MAX_CATEGORY_BUTTONS; i++) {
            int slot = i + 1;
            String labelPath = "#CategoryButton" + slot + "Label.Text";
            if (i < categories.size()) {
                CreditConfig.CategoryEntry category = categories.get(i);
                uiCommandBuilder.set(labelPath, category.name());
                uiEventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        "#CategoryButton" + slot,
                        EventData.of("action", "category:" + category.key())
                );
            } else {
                uiCommandBuilder.set(labelPath, "");
            }
        }

        if (selectedCategoryKey == null || selectedCategoryKey.isBlank()) {
            uiCommandBuilder.set("#CategoryContentLabel.Text", "Select a category.");
            uiCommandBuilder.set("#ItemsScrollContainer.Visible", "false");
            uiCommandBuilder.set("#ItemsScrollContainer.Anchor", SCROLL_CONTAINER_HIDDEN_ANCHOR);
            clearItemRows(uiCommandBuilder);
        } else {
            uiCommandBuilder.set("#ItemsScrollContainer.Visible", "true");
            uiCommandBuilder.set("#ItemsScrollContainer.Anchor", SCROLL_CONTAINER_VISIBLE_ANCHOR);

            Map<String, ShopItem> itemMap = shopLoader.loadCategoryItems(selectedCategoryKey);
            logger.info("[CreditSystem] Category selected key=" + selectedCategoryKey + " file="
                    + shopLoader.ensureCategoryFile(selectedCategoryKey).toAbsolutePath());

            if (itemMap.isEmpty()) {
                uiCommandBuilder.set("#CategoryContentLabel.Text", "No items configured in " + selectedCategoryKey + ".json");
            } else {
                uiCommandBuilder.set("#CategoryContentLabel.Text", "");
            }

            int index = 0;
            for (Map.Entry<String, ShopItem> entry : itemMap.entrySet()) {
                if (index >= MAX_ITEM_ROWS) {
                    break;
                }

                int slot = index + 1;
                ShopItem item = entry.getValue();
                uiCommandBuilder.set("#ItemRow" + slot + ".Visible", "true");
                uiCommandBuilder.set("#ItemRow" + slot + "Name.Text", item.name());
                uiCommandBuilder.set("#ItemRow" + slot + "Price.Text", item.price() + " " + config.currencyName());
                uiCommandBuilder.set("#ItemRow" + slot + "Desc.Text", item.description() == null ? "" : item.description());
                uiCommandBuilder.set("#ItemRow" + slot + "BuyLabel.Text", "Buy");
                uiEventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        "#ItemRow" + slot + "Buy",
                        EventData.of("action", "buy:" + selectedCategoryKey + ":" + entry.getKey())
                );
                index++;
            }

            for (int i = index + 1; i <= MAX_ITEM_ROWS; i++) {
                clearRow(uiCommandBuilder, i);
            }

            if (itemMap.size() > MAX_ITEM_ROWS) {
                uiCommandBuilder.set("#CategoryContentLabel.Text", "Showing first " + MAX_ITEM_ROWS + " items. Increase UI slot count for more.");
            }
        }

        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("action", "close")
        );
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, String eventData) {
        String action = extractAction(eventData);
        logger.info("[CreditSystem] Credit shop event received: " + eventData + " action=" + action);

        if ("close".equalsIgnoreCase(action)) {
            close();
            return;
        }

        if (action.startsWith("category:")) {
            handleCategorySelection(action.substring("category:".length()).trim());
            rebuild();
            return;
        }

        if (action.startsWith("buy:")) {
            handleBuyAction(action);
            rebuild();
            return;
        }

        logger.warning("[CreditSystem] Unknown credit shop action: " + action);
        rebuild();
    }

    private void handleCategorySelection(String selectedKey) {
        CreditConfig.CategoryEntry selected = config.categories().stream()
                .filter(category -> category.key().equalsIgnoreCase(selectedKey))
                .findFirst()
                .orElse(null);

        if (selected == null) {
            logger.warning("[CreditSystem] Unknown category action key: " + selectedKey);
            selectedCategoryKey = null;
            selectedCategoryName = "";
            return;
        }

        selectedCategoryKey = selected.key();
        selectedCategoryName = selected.name();
        logger.info("[CreditSystem] Category selected key=" + selectedCategoryKey + " file="
                + shopLoader.ensureCategoryFile(selectedCategoryKey).toAbsolutePath());
    }

    private void handleBuyAction(String action) {
        String[] parts = action.split(":", 3);
        if (parts.length < 3) {
            playerRef.sendMessage(Message.raw("Invalid purchase action."));
            return;
        }

        String categoryKey = parts[1];
        String itemId = parts[2];

        Map<String, ShopItem> items = shopLoader.loadCategoryItems(categoryKey);
        ShopItem item = items.get(itemId);
        if (item == null) {
            playerRef.sendMessage(Message.raw("That shop item was not found."));
            logger.warning("[CreditSystem] Buy failed item missing category=" + categoryKey + " itemId=" + itemId);
            return;
        }

        UUID uuid = playerRef.getUuid();
        String username = playerRef.getUsername();
        long balance = creditsService.getBalance(uuid, username);

        if (balance < item.price()) {
            playerRef.sendMessage(Message.raw("You need " + item.price() + " " + config.currencyName() + " to buy " + item.name() + "."));
            logger.info("[CreditSystem] Buy failed insufficient balance itemId=" + itemId + " price=" + item.price() + " balance=" + balance);
            return;
        }

        boolean purchased = creditsService.tryPurchase(uuid, username, item.price());
        long resulting = creditsService.getBalance(uuid, username);
        if (!purchased) {
            playerRef.sendMessage(Message.raw("Purchase failed. Please try again."));
            logger.info("[CreditSystem] Buy failed race itemId=" + itemId + " price=" + item.price() + " balanceNow=" + resulting);
            return;
        }

        for (String rawCommand : item.commands()) {
            String command = normalizeCommand(rawCommand, username, uuid);
            try {
                CommandManager.get().handleCommand(playerRef, command);
                logger.info("[CreditSystem] Executed shop command: " + command);
            } catch (Exception commandError) {
                logger.log(Level.SEVERE, "[CreditSystem] Failed executing shop command: " + command, commandError);
            }
        }

        playerRef.sendMessage(Message.raw("Purchased " + item.name() + " for " + item.price() + " " + config.currencyName() + "."));
        logger.info("[CreditSystem] Buy success itemId=" + itemId + " price=" + item.price() + " resultingBalance=" + resulting);
    }

    private void clearItemRows(UICommandBuilder uiCommandBuilder) {
        for (int i = 1; i <= MAX_ITEM_ROWS; i++) {
            clearRow(uiCommandBuilder, i);
        }
    }

    private void clearRow(UICommandBuilder uiCommandBuilder, int slot) {
        uiCommandBuilder.set("#ItemRow" + slot + ".Visible", "false");
        uiCommandBuilder.set("#ItemRow" + slot + "Name.Text", "");
        uiCommandBuilder.set("#ItemRow" + slot + "Price.Text", "");
        uiCommandBuilder.set("#ItemRow" + slot + "Desc.Text", "");
        uiCommandBuilder.set("#ItemRow" + slot + "BuyLabel.Text", "");
    }

    private String extractAction(String eventData) {
        if (eventData == null || eventData.isBlank()) {
            return "";
        }

        try {
            JsonObject obj = JsonParser.parseString(eventData).getAsJsonObject();
            if (obj.has("action") && !obj.get("action").isJsonNull()) {
                return obj.get("action").getAsString();
            }
        } catch (Exception parseError) {
            logger.log(Level.WARNING, "[CreditSystem] Failed to parse credit shop event payload", parseError);
        }
        return "";
    }

    private String normalizeCommand(String command, String player, UUID uuid) {
        String normalized = command.replace("{player}", player).replace("{uuid}", uuid.toString());
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.trim();
    }

    private boolean validateUiMarkupSafely() {
        try (InputStream stream = getClass().getResourceAsStream("/" + UI_RESOURCE_PATH)) {
            if (stream == null) {
                return false;
            }
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (content.contains("Button.Text:")) {
                logger.severe("[CreditSystem] Invalid UI markup: Button.Text detected.");
                return false;
            }
            if (content.contains("Group Style:")) {
                logger.severe("[CreditSystem] Invalid UI markup: Group Style detected.");
                return false;
            }
            if (content.contains("Alignment: Left") || content.contains("Alignment: Right")) {
                logger.severe("[CreditSystem] Invalid UI markup: unsupported alignment token.");
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[CreditSystem] Failed to validate UI markup", e);
            return false;
        }
    }
}
