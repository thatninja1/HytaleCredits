package dev.hytalemodding.creditsystem.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.creditsystem.config.CreditConfig;
import dev.hytalemodding.creditsystem.service.CreditsService;

import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HytaleCreditShopPage extends CustomUIPage {
    private static final String UI_TEMPLATE = "Pages/Credits/CreditShop.ui";
    private static final String UI_RESOURCE_PATH = "Common/UI/Custom/Pages/Credits/CreditShop.ui";
    private static final int MAX_CATEGORY_BUTTONS = 12;

    private final CreditsService creditsService;
    private final CreditConfig config;
    private final Logger logger;

    private String activeTitle;
    private String contentText;

    public HytaleCreditShopPage(PlayerRef playerRef, CreditsService creditsService, CreditConfig config, Logger logger) {
        super(playerRef, CustomPageLifetime.CanDismiss);
        this.creditsService = creditsService;
        this.config = config;
        this.logger = logger;
        this.activeTitle = config.ui().title();
        this.contentText = "Select a category.";
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

        uiCommandBuilder.set("#TitleLabel.Text", activeTitle);
        if (!creditsService.isOnline()) {
            uiCommandBuilder.set("#CreditsBalanceLabel.Text", "Credits system unavailable");
        } else {
            long balance = creditsService.getBalance(playerRef.getUuid(), playerRef.getUsername());
            uiCommandBuilder.set("#CreditsBalanceLabel.Text", config.currencyName() + ": " + balance);
        }
        uiCommandBuilder.set("#CategoryContentLabel.Text", contentText);

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

        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("action", "close")
        );
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, String eventData) {
        if (eventData == null || eventData.isBlank()) {
            return;
        }

        logger.info("[CreditSystem] Credit shop event received: " + eventData);

        if (eventData.contains("close")) {
            logger.info("[CreditSystem] Credit shop close event for " + playerRef.getUsername());
            close();
            return;
        }

        int categoryIndex = eventData.indexOf("category:");
        if (categoryIndex < 0) {
            return;
        }

        String selectedKey = eventData.substring(categoryIndex + "category:".length()).trim();
        CreditConfig.CategoryEntry selected = config.categories().stream()
                .filter(category -> category.key().equalsIgnoreCase(selectedKey))
                .findFirst()
                .orElse(null);

        if (selected == null) {
            logger.warning("[CreditSystem] Unknown category action key: " + selectedKey);
            return;
        }

        this.activeTitle = selected.name();
        this.contentText = "Coming soon: " + selected.name();
        logger.info("[CreditSystem] Credit shop category selected: " + selected.key());
        rebuild();
    }

    private boolean validateUiMarkupSafely() {
        try (InputStream stream = getClass().getResourceAsStream("/" + UI_RESOURCE_PATH)) {
            if (stream == null) {
                return false;
            }
            String content = new String(stream.readAllBytes());
            if (content.contains("Button.Text:")) {
                logger.severe("[CreditSystem] Invalid UI markup: Button.Text detected.");
                return false;
            }
            if (content.contains("Group Style:")) {
                logger.severe("[CreditSystem] Invalid UI markup: Group Style detected.");
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[CreditSystem] Failed to validate UI markup", e);
            return false;
        }
    }
}
