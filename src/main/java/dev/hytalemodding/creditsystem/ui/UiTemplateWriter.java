package dev.hytalemodding.creditsystem.ui;

import dev.hytalemodding.creditsystem.config.CreditConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UiTemplateWriter {
    private static final String EMPTY_RESOURCE = "Common/UI/Custom/Pages/Credits/CreditShopEmpty.ui";
    private static final String ITEMS_RESOURCE = "Common/UI/Custom/Pages/Credits/CreditShopItems.ui";

    private UiTemplateWriter() {
    }

    public static void writeShopUiFiles(CreditConfig config, Logger logger) {
        try {
            writeOne(config, logger, EMPTY_RESOURCE, false);
            writeOne(config, logger, ITEMS_RESOURCE, true);
        } catch (Exception e) {
            logger.warning("[CreditSystem] Failed writing themed UI templates: " + e.getMessage());
        }
    }

    private static void writeOne(CreditConfig config, Logger logger, String resourcePath, boolean itemsTemplate) throws IOException {
        String template = loadTemplate(resourcePath);
        if (template == null || template.isBlank()) {
            logger.warning("[CreditSystem] Missing UI template resource: " + resourcePath);
            return;
        }

        CreditConfig.UiTheme theme = config.ui().theme();

        String themed = template;
        themed = applyStyle(themed, "#TitleLabel", style(theme.title(), "Center", 46, "#E5E7EB"), logger, resourcePath);
        themed = applyStyle(themed, "#CreditsBalanceLabel", style(theme.credits(), "Center", 24, "#93C5FD"), logger, resourcePath);
        themed = applyStyle(themed, "#CloseButtonLabel", style(theme.closeButton(), "Center", 16, "#E2E8F0"), logger, resourcePath);

        if (itemsTemplate) {
            themed = applyStyle(themed, "#SelectedCategoryLabel", style(theme.selectedCategory(), "Center", 20, "#CBD5E1"), logger, resourcePath);
            themed = applyStyle(themed, "#PageIndicatorLabel", style(theme.pageIndicator(), "Center", 18, "#CBD5E1"), logger, resourcePath);
            themed = applyStyle(themed, "#PrevPageButtonLabel", style(theme.paginationButton(), "Center", 16, "#E2E8F0"), logger, resourcePath);
            themed = applyStyle(themed, "#NextPageButtonLabel", style(theme.paginationButton(), "Center", 16, "#E2E8F0"), logger, resourcePath);
        } else {
            themed = applyStyle(themed, "#CategoryPromptLabel", style(theme.selectedCategory(), "Center", 24, "#CBD5E1"), logger, resourcePath);
        }

        for (int i = 1; i <= 12; i++) {
            themed = applyStyle(themed, "#CategoryButton" + i + "Label", style(theme.categoryButton(), "Start", 18, "#FDE047"), logger, resourcePath);
        }

        if (itemsTemplate) {
            for (int i = 1; i <= 5; i++) {
                themed = applyStyle(themed, "#ItemCard" + i + "Name", style(theme.itemName(), "Center", 20, "#F8FAFC"), logger, resourcePath);
                themed = applyStyle(themed, "#ItemCard" + i + "Price", style(theme.itemPrice(), "Center", 18, "#FDE047"), logger, resourcePath);
                themed = applyStyle(themed, "#ItemCard" + i + "Desc", style(theme.itemDescription(), "Start", 13, "#CBD5E1"), logger, resourcePath);
                themed = applyStyle(themed, "#ItemCard" + i + "BuyLabel", style(theme.buyLabel(), "Center", 16, "#E2E8F0"), logger, resourcePath);
            }
            themed = themed.replace("Anchor: (Left: -8, Width: 1028, Height: 480);", "Anchor: (Left: -8, Width: 1028, Height: 480);");
            themed = themed.replace("Anchor: (Top: 0, Left: -150, Width: 1028, Height: 406);", "Anchor: (Top: 0, Left: -150, Width: 1028, Height: 406);");
            themed = themed.replace("Anchor: (Top: 406, Left: -150, Width: 1028, Height: 50);", "Anchor: (Top: 406, Left: -150, Width: 1028, Height: 50);");
        }

        Path diskPath = Path.of(resourcePath);
        Files.createDirectories(diskPath.getParent());
        Files.writeString(diskPath, themed, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        logger.info("[CreditSystem] Wrote themed UI template: " + diskPath.toAbsolutePath());
    }

    private static String loadTemplate(String resourcePath) throws IOException {
        try (InputStream in = UiTemplateWriter.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String applyStyle(String content, String selector, String styleString, Logger logger, String resourcePath) {
        Pattern pattern = Pattern.compile("(" + Pattern.quote(selector) + "\\s*\\{[\\s\\S]*?Style:\\s*\\()([^)]*)(\\);)");
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            logger.warning("[CreditSystem] Could not find selector in template " + resourcePath + ": " + selector);
            return content;
        }
        return matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + styleString + matcher.group(3)));
    }

    private static String style(CreditConfig.TextStyle style, String alignment, int fallbackSize, String fallbackColor) {
        int size = fallbackSize;
        String color = fallbackColor;

        if (style != null) {
            if (style.fontSize() >= 8 && style.fontSize() <= 72) {
                size = style.fontSize();
            }
            if (style.color() != null && style.color().matches("^#[0-9A-Fa-f]{6}$")) {
                color = style.color();
            }
        }

        return "FontSize: " + size + ", Alignment: " + alignment + ", TextColor: " + color;
    }
}
