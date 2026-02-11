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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HytaleCreditShopPage extends CustomUIPage {
    private static final String UI_TEMPLATE = "Pages/Credits/CreditShop.ui";
    private static final String UI_RESOURCE_PATH = "Common/UI/Custom/Pages/Credits/CreditShop.ui";
    private static final int MAX_CATEGORY_BUTTONS = 12;
    private static final int PAGE_SIZE = 5;

    private static final String ITEMS_PANEL_VISIBLE_ANCHOR = "(Top: 14, Left: 10, Width: 960, Height: 500)";
    private static final String ITEMS_PANEL_HIDDEN_ANCHOR = "(Top: 14, Left: 10, Width: 960, Height: 0)";

    private final CreditsService creditsService;
    private final CreditConfig config;
    private final Logger logger;
    private final CategoryShopLoader shopLoader;

    private String selectedCategoryKey;
    private String selectedCategoryName;
    private int currentPage;

    public HytaleCreditShopPage(PlayerRef playerRef, CreditsService creditsService, CreditConfig config, Logger logger) {
        super(playerRef, CustomPageLifetime.CanDismiss);
        this.creditsService = creditsService;
        this.config = config;
        this.logger = logger;
        this.shopLoader = new CategoryShopLoader(logger);
        this.currentPage = 0;

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

        uiCommandBuilder.append(UI_TEMPLATE);

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
            uiCommandBuilder.set("#CategoryPromptLabel.Text", "Select a category.");
            uiCommandBuilder.set("#ItemsPanel.Anchor", ITEMS_PANEL_HIDDEN_ANCHOR);
            uiCommandBuilder.set("#PageIndicatorLabel.Text", "");
            uiCommandBuilder.set("#PrevPageButtonLabel.Text", "");
            uiCommandBuilder.set("#NextPageButtonLabel.Text", "");
            clearCards(uiCommandBuilder);
        } else {
            uiCommandBuilder.set("#CategoryPromptLabel.Text", "");
            uiCommandBuilder.set("#ItemsPanel.Anchor", ITEMS_PANEL_VISIBLE_ANCHOR);

            Map<String, ShopItem> itemMap = shopLoader.loadCategoryItems(selectedCategoryKey);
            List<Map.Entry<String, ShopItem>> allItems = new ArrayList<>(itemMap.entrySet());
            int totalPages = Math.max(1, (int) Math.ceil(allItems.size() / (double) PAGE_SIZE));
            currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));
            int start = currentPage * PAGE_SIZE;

            for (int card = 1; card <= PAGE_SIZE; card++) {
                int index = start + (card - 1);
                if (index < allItems.size()) {
                    Map.Entry<String, ShopItem> entry = allItems.get(index);
                    ShopItem item = entry.getValue();
                    uiCommandBuilder.set("#ItemCard" + card + "Name.Text", item.name());
                    uiCommandBuilder.set("#ItemCard" + card + "Price.Text", item.price() + " " + config.currencyName());
                    uiCommandBuilder.set("#ItemCard" + card + "Desc.Text", item.description() == null ? "" : item.description());
                    uiCommandBuilder.set("#ItemCard" + card + "BuyLabel.Text", "Buy");
                    uiEventBuilder.addEventBinding(
                            CustomUIEventBindingType.Activating,
                            "#ItemCard" + card + "Buy",
                            EventData.of("action", "buy:" + selectedCategoryKey + ":" + entry.getKey())
                    );
                } else {
                    clearCard(uiCommandBuilder, card);
                }
            }

            uiCommandBuilder.set("#PageIndicatorLabel.Text", "Page " + (currentPage + 1) + "/" + totalPages);
            uiCommandBuilder.set("#PrevPageButtonLabel.Text", currentPage > 0 ? "Prev" : "");
            uiCommandBuilder.set("#NextPageButtonLabel.Text", currentPage < totalPages - 1 ? "Next" : "");

            if (currentPage > 0) {
                uiEventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        "#PrevPageButton",
                        EventData.of("action", "page:prev")
                );
            }
            if (currentPage < totalPages - 1) {
                uiEventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        "#NextPageButton",
                        EventData.of("action", "page:next")
                );
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

        if ("close".equalsIgnoreCase(action)) {
            close();
            return;
        }

        if (action.startsWith("category:")) {
            handleCategorySelection(action.substring("category:".length()).trim());
            rebuild();
            return;
        }

        if ("page:prev".equalsIgnoreCase(action)) {
            if (currentPage > 0) {
                currentPage--;
            }
            rebuild();
            return;
        }

        if ("page:next".equalsIgnoreCase(action)) {
            int totalPages = resolveTotalPages();
            if (currentPage < totalPages - 1) {
                currentPage++;
            }
            rebuild();
            return;
        }

        if (action.startsWith("buy:")) {
            handleBuyAction(action);
            rebuild();
            return;
        }

        rebuild();
    }

    private int resolveTotalPages() {
        if (selectedCategoryKey == null || selectedCategoryKey.isBlank()) {
            return 1;
        }
        int size = shopLoader.loadCategoryItems(selectedCategoryKey).size();
        return Math.max(1, (int) Math.ceil(size / (double) PAGE_SIZE));
    }

    private void handleCategorySelection(String selectedKey) {
        CreditConfig.CategoryEntry selected = config.categories().stream()
                .filter(category -> category.key().equalsIgnoreCase(selectedKey))
                .findFirst()
                .orElse(null);

        if (selected == null) {
            selectedCategoryKey = null;
            selectedCategoryName = "";
            currentPage = 0;
            return;
        }

        selectedCategoryKey = selected.key();
        selectedCategoryName = selected.name();
        currentPage = 0;
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
            return;
        }

        boolean purchased = creditsService.tryPurchase(uuid, username, item.price());
        if (!purchased) {
            playerRef.sendMessage(Message.raw("Purchase failed. Please try again."));
            return;
        }

        for (String rawCommand : item.commands()) {
            String command = normalizeCommand(rawCommand, username, uuid);
            try {
                CommandManager.get().handleCommand(playerRef, command);
            } catch (Exception commandError) {
                logger.log(Level.SEVERE, "[CreditSystem] Failed executing shop command: " + command, commandError);
            }
        }

        playerRef.sendMessage(Message.raw("Purchased " + item.name() + " for " + item.price() + " " + config.currencyName() + "."));
    }

    private void clearCards(UICommandBuilder uiCommandBuilder) {
        for (int i = 1; i <= PAGE_SIZE; i++) {
            clearCard(uiCommandBuilder, i);
        }
    }

    private void clearCard(UICommandBuilder uiCommandBuilder, int slot) {
        uiCommandBuilder.set("#ItemCard" + slot + "Name.Text", "");
        uiCommandBuilder.set("#ItemCard" + slot + "Price.Text", "");
        uiCommandBuilder.set("#ItemCard" + slot + "Desc.Text", "");
        uiCommandBuilder.set("#ItemCard" + slot + "BuyLabel.Text", "");
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
            if (content.contains("ScrollView")) {
                logger.severe("[CreditSystem] Invalid UI markup: unsupported ScrollView node detected.");
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[CreditSystem] Failed to validate UI markup", e);
            return false;
        }
    }
}
