package dev.hytalemodding.creditsystem.ui;

import dev.hytalemodding.creditsystem.commands.PlayerRef;
import dev.hytalemodding.creditsystem.config.CreditConfig;
import dev.hytalemodding.creditsystem.service.CreditsService;

import java.util.List;
import java.util.logging.Logger;

/**
 * Framework-agnostic credit shop page state manager.
 *
 * <p>Bind to your runtime CustomUIPage by providing concrete builders:
 * - commandBuilder.append("Pages/Credits/CreditShop.ui")
 * - commandBuilder.set("#SomeLabel.Text", "value")
 * - eventBuilder.addActivatingBinding("#SomeButton", "action")
 * - call {@link #handleDataEvent(String)} from your page data event callback.</p>
 */
public final class CreditShopPage {
    public static final int MAX_CATEGORY_BUTTONS = 12;

    private final PlayerRef player;
    private final CreditsService creditsService;
    private final CreditConfig config;
    private final Logger logger;

    private String activeTitle;
    private String contentText;

    public CreditShopPage(PlayerRef player, CreditsService creditsService, CreditConfig config, Logger logger) {
        this.player = player;
        this.creditsService = creditsService;
        this.config = config;
        this.logger = logger;
        this.activeTitle = config.ui().title();
        this.contentText = "Select a category.";
    }

    public void appendUi(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.append("Pages/Credits/CreditShop.ui");
        commandBuilder.set("#TitleLabel.Text", activeTitle);
        commandBuilder.set("#CreditsBalanceLabel.Text", config.currencyName() + ": " + creditsService.getBalance(player.uuid(), player.name()));
        commandBuilder.set("#CategoryContentLabel.Text", contentText);

        List<CreditConfig.CategoryEntry> categories = config.categories();
        for (int i = 0; i < MAX_CATEGORY_BUTTONS; i++) {
            int slot = i + 1;
            String labelPath = "#CategoryButton" + slot + "Label.Text";
            if (i < categories.size()) {
                CreditConfig.CategoryEntry category = categories.get(i);
                commandBuilder.set(labelPath, category.name());
                eventBuilder.addActivatingBinding("#CategoryButton" + slot, "category:" + category.key());
            } else {
                commandBuilder.set(labelPath, "");
            }
        }

        if (categories.size() > MAX_CATEGORY_BUTTONS) {
            logger.warning("Credit shop has more than 12 categories in config.json. Extra categories are ignored.");
        }

        eventBuilder.addActivatingBinding("#CloseButton", "close");
    }

    public boolean handleDataEvent(String eventData) {
        if (eventData == null || eventData.isBlank()) {
            return false;
        }

        if (eventData.contains("close")) {
            return true;
        }

        String actionPrefix = "category:";
        int idx = eventData.indexOf(actionPrefix);
        if (idx < 0) {
            return false;
        }

        String selectedKey = eventData.substring(idx + actionPrefix.length()).trim();
        CreditConfig.CategoryEntry selected = config.categories().stream()
                .filter(category -> category.key().equalsIgnoreCase(selectedKey))
                .findFirst()
                .orElse(null);

        if (selected == null) {
            return false;
        }

        this.activeTitle = selected.name();
        this.contentText = "Coming soon: " + selected.name();
        return false;
    }

    public interface UICommandBuilder {
        void append(String uiPath);

        void set(String path, String value);
    }

    public interface UIEventBuilder {
        void addActivatingBinding(String elementPath, String action);
    }
}
