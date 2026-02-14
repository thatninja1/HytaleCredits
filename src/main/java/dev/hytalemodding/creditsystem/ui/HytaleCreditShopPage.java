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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HytaleCreditShopPage extends CustomUIPage {
    private static final int MAX_CATEGORY_BUTTONS = 12;
    private static final int PAGE_SIZE = 5;
    private static final int DESCRIPTION_MAX_LINES = 12;
    private static final int DESCRIPTION_BASE_CHARS = 22;
    private static final long BALANCE_TTL_MS = 15000L;
    private static final long EVENT_DEDUPE_WINDOW_MS = 150L;

    private final CreditsService creditsService;
    private final Supplier<CreditConfig> configSupplier;
    private final Logger logger;
    private final CategoryShopLoader shopLoader;

    private String selectedCategoryKey;
    private String selectedCategoryName;
    private int currentPage;
    private boolean uiResourcesChecked;
    private Long cachedBalance;
    private long cachedBalanceAtMs;
    private String lastAction;
    private long lastActionAtMs;

    public HytaleCreditShopPage(PlayerRef playerRef,
                                CreditsService creditsService,
                                Supplier<CreditConfig> configSupplier,
                                CategoryShopLoader shopLoader,
                                Logger logger) {
        super(playerRef, CustomPageLifetime.CanDismiss);
        this.creditsService = creditsService;
        this.configSupplier = configSupplier;
        this.shopLoader = shopLoader;
        this.logger = logger;
        this.currentPage = 0;
        this.uiResourcesChecked = false;
        this.cachedBalance = null;
        this.cachedBalanceAtMs = 0L;
        this.lastAction = "";
        this.lastActionAtMs = 0L;

        CreditConfig activeConfig = currentConfig();
        for (CreditConfig.CategoryEntry category : activeConfig.categories()) {
            shopLoader.ensureCategoryFile(category.key());
        }
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder uiCommandBuilder, UIEventBuilder uiEventBuilder, Store<EntityStore> store) {
        CreditConfig config = currentConfig();
        boolean canUseThemedFiles;
        if (!uiResourcesChecked || config.debug()) {
            canUseThemedFiles = ensureUiResourceExists(UiTemplateWriter.EMPTY_DISK_PATH)
                    && ensureUiResourceExists(UiTemplateWriter.ITEMS_DISK_PATH)
                    && validateUiMarkupSafely(UiTemplateWriter.EMPTY_DISK_PATH)
                    && validateUiMarkupSafely(UiTemplateWriter.ITEMS_DISK_PATH);
            if (!canUseThemedFiles) {
                logger.warning("[CreditSystem] UI files missing/invalid. Regenerating themed templates once.");
                UiTemplateWriter.writeThemedTemplates(config, logger);
                canUseThemedFiles = ensureUiResourceExists(UiTemplateWriter.EMPTY_DISK_PATH)
                        && ensureUiResourceExists(UiTemplateWriter.ITEMS_DISK_PATH);
            }
            uiResourcesChecked = canUseThemedFiles;
        } else {
            canUseThemedFiles = true;
        }

        if (!canUseThemedFiles) {
            playerRef.sendMessage(Message.raw("Failed to open Credit Shop UI (resource missing)."));
            close();
            return;
        }

        if (config.debug()) {
            logger.info("[CreditSystem] /creditshop using in-memory theme: " + summarizeTheme(config.ui().theme()));
        }
        boolean hasCategory = selectedCategoryKey != null && !selectedCategoryKey.isBlank();
        String template = hasCategory ? UiTemplateWriter.ITEMS_RESOURCE_PATH : UiTemplateWriter.EMPTY_RESOURCE_PATH;
        logger.info("[CreditSystem] Appending UI document: " + template);
        uiCommandBuilder.append(template);

        uiCommandBuilder.set("#TitleLabel.Text", config.ui().title());
        if (!creditsService.isOnline()) {
            uiCommandBuilder.set("#CreditsBalanceLabel.Text", "Credits system unavailable");
        } else {
            long balance = getCachedBalance();
            uiCommandBuilder.set("#CreditsBalanceLabel.Text", config.currencyName() + ": " + balance);
        }

        bindCategoryButtons(config, uiCommandBuilder, uiEventBuilder);
        bindCloseButton(uiEventBuilder);

        if (!hasCategory) {
            uiCommandBuilder.set("#CategoryPromptLabel.Text", "Select a category.");
            return;
        }

        uiCommandBuilder.set("#SelectedCategoryLabel.Text", selectedCategoryName == null ? "" : selectedCategoryName);
        renderPagedItems(config, uiCommandBuilder, uiEventBuilder);
    }

    private CreditConfig currentConfig() {
        CreditConfig cfg = configSupplier.get();
        return cfg == null ? CreditConfig.defaults() : cfg;
    }


    private String summarizeTheme(CreditConfig.UiTheme theme) {
        if (theme == null) {
            return "theme=null";
        }
        return "title=" + theme.title().color() + "/" + theme.title().fontSize()
                + ", credits=" + theme.credits().color() + "/" + theme.credits().fontSize()
                + ", categoryButton=" + theme.categoryButton().color() + "/" + theme.categoryButton().fontSize()
                + ", itemName=" + theme.itemName().color() + "/" + theme.itemName().fontSize();
    }

    private void bindCategoryButtons(CreditConfig config, UICommandBuilder uiCommandBuilder, UIEventBuilder uiEventBuilder) {
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
    }

    private void bindCloseButton(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("action", "close")
        );
    }

    private void renderPagedItems(CreditConfig config, UICommandBuilder uiCommandBuilder, UIEventBuilder uiEventBuilder) {
        Map<String, ShopItem> itemMap = shopLoader.loadCategoryItems(selectedCategoryKey);
        List<Map.Entry<String, ShopItem>> allItems = new ArrayList<>(itemMap.entrySet());
        if (config.debug()) {
            String order = String.join(", ", allItems.stream().map(Map.Entry::getKey).toList());
            logger.info("[CreditSystem] Loaded shop order for " + selectedCategoryKey + ": " + order);
        }
        int totalPages = Math.max(1, (int) Math.ceil(allItems.size() / (double) PAGE_SIZE));
        currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allItems.size());
        int count = Math.max(0, end - start);
        int startSlot = 1;

        for (int card = 1; card <= PAGE_SIZE; card++) {
            clearCard(uiCommandBuilder, card);
            uiCommandBuilder.set("#ItemCard" + card + "Buy.Visible", false);
        }

        for (int i = 0; i < count; i++) {
            int slot = startSlot + i;
            Map.Entry<String, ShopItem> entry = allItems.get(start + i);
            ShopItem item = entry.getValue();

            uiCommandBuilder.set("#ItemCard" + slot + "Name.Text", item.name());
            uiCommandBuilder.set("#ItemCard" + slot + "Price.Text", item.price() + " " + config.currencyName());

            int charsPerLine = DESCRIPTION_BASE_CHARS;
            uiCommandBuilder.set("#ItemCard" + slot + "Desc.Text", wrapForUi(item.description(), charsPerLine, DESCRIPTION_MAX_LINES));
            uiCommandBuilder.set("#ItemCard" + slot + "BuyLabel.Text", "Buy");
            uiCommandBuilder.set("#ItemCard" + slot + "Buy.Visible", true);

            uiEventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#ItemCard" + slot + "Buy",
                    EventData.of("action", "buy:" + selectedCategoryKey + ":" + entry.getKey())
            );
        }

        applyPageControls(uiCommandBuilder, uiEventBuilder, totalPages);
    }


    private void applyPageControls(UICommandBuilder uiCommandBuilder, UIEventBuilder uiEventBuilder, int totalPages) {
        boolean hasPrev = currentPage > 0;
        boolean hasNext = currentPage < totalPages - 1;

        uiCommandBuilder.set("#PageIndicatorLabel.Text", "Page " + (currentPage + 1) + "/" + totalPages);

        uiCommandBuilder.set("#PrevPageButton.Visible", hasPrev);
        uiCommandBuilder.set("#PrevPageButtonLabel.Text", hasPrev ? "Prev" : "");

        uiCommandBuilder.set("#NextPageButton.Visible", hasNext);
        uiCommandBuilder.set("#NextPageButtonLabel.Text", hasNext ? "Next" : "");

        if (hasPrev) {
            uiEventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#PrevPageButton",
                    EventData.of("action", "page:prev")
            );
        }
        if (hasNext) {
            uiEventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#NextPageButton",
                    EventData.of("action", "page:next")
            );
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, String eventData) {
        String action = extractAction(eventData);
        if (action == null || action.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (action.equals(lastAction) && (now - lastActionAtMs) <= EVENT_DEDUPE_WINDOW_MS) {
            return;
        }
        lastAction = action;
        lastActionAtMs = now;

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
        CreditConfig config = currentConfig();
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

        CreditConfig config = currentConfig();
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

        if (cachedBalance != null) {
            cachedBalance = Math.max(0L, cachedBalance - item.price());
            cachedBalanceAtMs = System.currentTimeMillis();
        }

        playerRef.sendMessage(Message.raw("Purchased " + item.name() + " for " + item.price() + " " + config.currencyName() + "."));
    }


    private long getCachedBalance() {
        long now = System.currentTimeMillis();
        if (cachedBalance != null && (now - cachedBalanceAtMs) < BALANCE_TTL_MS) {
            return cachedBalance;
        }
        long latest = creditsService.getBalance(playerRef.getUuid(), playerRef.getUsername());
        cachedBalance = latest;
        cachedBalanceAtMs = now;
        return latest;
    }

    private void clearCard(UICommandBuilder uiCommandBuilder, int slot) {
        uiCommandBuilder.set("#ItemCard" + slot + "Name.Text", "");
        uiCommandBuilder.set("#ItemCard" + slot + "Price.Text", "");
        uiCommandBuilder.set("#ItemCard" + slot + "Desc.Text", "");
        uiCommandBuilder.set("#ItemCard" + slot + "BuyLabel.Text", "");
    }

    private String wrapForUi(String text, int maxCharsPerLine, int maxLines) {
        if (text == null || text.isBlank() || maxCharsPerLine <= 0 || maxLines <= 0) {
            return "";
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) {
            return "";
        }

        String[] words = normalized.replace('\n', ' ').trim().split("\\s+");
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int index = 0;
        boolean truncated = false;

        while (index < words.length) {
            String word = words[index];
            if (word.isEmpty()) {
                index++;
                continue;
            }

            if (word.length() > maxCharsPerLine) {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    if (lines.size() == maxLines) {
                        truncated = true;
                        break;
                    }
                    current.setLength(0);
                }

                int start = 0;
                while (start < word.length()) {
                    int stop = Math.min(start + maxCharsPerLine, word.length());
                    lines.add(word.substring(start, stop));
                    if (lines.size() == maxLines) {
                        truncated = stop < word.length() || index < words.length - 1;
                        break;
                    }
                    start = stop;
                }

                if (truncated || lines.size() == maxLines) {
                    break;
                }

                index++;
                continue;
            }

            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= maxCharsPerLine) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                if (lines.size() == maxLines) {
                    truncated = true;
                    break;
                }
                current.setLength(0);
                current.append(word);
            }
            index++;
        }

        if (!truncated && current.length() > 0 && lines.size() < maxLines) {
            lines.add(current.toString());
        }

        if (lines.isEmpty()) {
            return "";
        }

        if (truncated) {
            int lastIndex = lines.size() - 1;
            String last = lines.get(lastIndex);
            int keep = Math.max(0, maxCharsPerLine - 3);
            if (last.length() > keep) {
                last = last.substring(0, keep);
            }
            lines.set(lastIndex, last + "...");
        }

        return String.join("\n", lines);
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

    private boolean ensureUiResourceExists(String resourcePath) {
        try {
            Path diskPath = Path.of(resourcePath);
            if (Files.exists(diskPath)) {
                logger.info("[CreditSystem] UI resource exists: " + diskPath.toAbsolutePath());
                return true;
            }

            try (InputStream stream = getClass().getResourceAsStream("/" + resourcePath)) {
                if (stream == null) {
                    logger.severe("[CreditSystem] UI resource missing (disk+jar): " + resourcePath);
                    return false;
                }
                Files.createDirectories(diskPath.getParent());
                Files.writeString(
                        diskPath,
                        new String(stream.readAllBytes(), StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                );
                logger.info("[CreditSystem] Copied UI resource to disk: " + diskPath.toAbsolutePath());
                return true;
            }
        } catch (Exception resourceException) {
            logger.log(Level.SEVERE, "[CreditSystem] Error checking UI resource: " + resourcePath, resourceException);
            return false;
        }
    }

    private String readUiContent(String resourcePath) {
        try {
            Path diskPath = Path.of(resourcePath);
            if (Files.exists(diskPath)) {
                return Files.readString(diskPath, StandardCharsets.UTF_8);
            }
            try (InputStream stream = getClass().getResourceAsStream("/" + resourcePath)) {
                if (stream == null) {
                    return null;
                }
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[CreditSystem] Failed reading UI content: " + resourcePath, e);
            return null;
        }
    }

    private boolean validateUiMarkupSafely(String resourcePath) {
        try {
            String content = readUiContent(resourcePath);
            if (content == null) {
                return false;
            }
            if (content.contains("Button.Text:")) {
                logger.severe("[CreditSystem] Invalid UI markup: Button.Text detected in " + resourcePath);
                return false;
            }
            if (content.contains("Group Style:")) {
                logger.severe("[CreditSystem] Invalid UI markup: Group Style detected in " + resourcePath);
                return false;
            }
            if (content.contains("Alignment: Left") || content.contains("Alignment: Right")) {
                logger.severe("[CreditSystem] Invalid UI markup: unsupported alignment token in " + resourcePath);
                return false;
            }
            if (content.contains("ScrollView")) {
                logger.severe("[CreditSystem] Invalid UI markup: unsupported ScrollView node detected in " + resourcePath);
                return false;
            }
            if (content.contains("TextWrap")) {
                logger.severe("[CreditSystem] Invalid UI markup: unsupported TextWrap property detected in " + resourcePath);
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[CreditSystem] Failed to validate UI markup: " + resourcePath, e);
            return false;
        }
    }
}
