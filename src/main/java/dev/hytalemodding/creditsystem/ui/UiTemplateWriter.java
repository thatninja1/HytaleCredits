package dev.hytalemodding.creditsystem.ui;

import dev.hytalemodding.creditsystem.config.CreditConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UiTemplateWriter {
    private static final String EMPTY_SOURCE_RESOURCE = "Common/UI/Custom/Pages/Credits/CreditShopEmpty.ui";
    private static final String ITEMS_SOURCE_RESOURCE = "Common/UI/Custom/Pages/Credits/CreditShopItems.ui";

    public static final String EMPTY_RESOURCE_PATH = "Pages/Credits/CreditShopEmpty.ui";
    public static final String ITEMS_RESOURCE_PATH = "Pages/Credits/CreditShopItems.ui";

    public static final String EMPTY_DISK_PATH = "Common/UI/Custom/Pages/Credits/CreditShopEmpty.ui";
    public static final String ITEMS_DISK_PATH = "Common/UI/Custom/Pages/Credits/CreditShopItems.ui";

    private UiTemplateWriter() {
    }

    public static void writeThemedTemplates(CreditConfig config, Logger logger) {
        CreditConfig.UiTheme theme = config == null || config.ui() == null ? null : config.ui().theme();
        if (theme == null) {
            logger.info("[CreditSystem] ui.theme missing; preserving existing disk UI files and only ensuring defaults exist.");
            ensureDefaultExists(EMPTY_SOURCE_RESOURCE, EMPTY_DISK_PATH, logger);
            ensureDefaultExists(ITEMS_SOURCE_RESOURCE, ITEMS_DISK_PATH, logger);
            return;
        }

        try {
            writeOne(config, logger, EMPTY_SOURCE_RESOURCE, EMPTY_DISK_PATH, false);
            writeOne(config, logger, ITEMS_SOURCE_RESOURCE, ITEMS_DISK_PATH, true);
        } catch (Exception e) {
            logger.warning("[CreditSystem] Failed writing themed UI templates: " + e.getMessage());
        }
    }

    private static void ensureDefaultExists(String sourceResourcePath, String diskOutputPath, Logger logger) {
        try {
            Path outputPath = Path.of(diskOutputPath);
            if (Files.exists(outputPath)) {
                return;
            }
            String template = loadTemplate(sourceResourcePath);
            if (template == null || template.isBlank()) {
                logger.warning("[CreditSystem] Missing UI template resource: " + sourceResourcePath);
                return;
            }
            atomicWrite(outputPath, template);
            logger.info("[CreditSystem] Created default UI template: " + outputPath.toAbsolutePath());
        } catch (Exception e) {
            logger.warning("[CreditSystem] Failed ensuring default UI template " + diskOutputPath + ": " + e.getMessage());
        }
    }

    private static void writeOne(CreditConfig config,
                                 Logger logger,
                                 String sourceResourcePath,
                                 String diskOutputPath,
                                 boolean itemsTemplate) throws IOException {
        String template = loadTemplate(sourceResourcePath);
        if (template == null || template.isBlank()) {
            logger.warning("[CreditSystem] Missing UI template resource: " + sourceResourcePath);
            return;
        }

        CreditConfig.UiTheme theme = config.ui().theme();

        String themed = template;
        String titleStyle = style(theme.title(), "Center", 46, "#E5E7EB");
        themed = applyStyle(themed, "#TitleLabel", titleStyle, logger, sourceResourcePath);
        themed = applyStyle(themed, "#CreditsBalanceLabel", style(theme.credits(), "Center", 24, "#93C5FD"), logger, sourceResourcePath);
        themed = applyStyle(themed, "#CloseButtonLabel", style(theme.closeButton(), "Center", 16, "#E2E8F0"), logger, sourceResourcePath);

        if (itemsTemplate) {
            themed = applyStyle(themed, "#SelectedCategoryLabel", style(theme.selectedCategory(), "Center", 20, "#CBD5E1"), logger, sourceResourcePath);
            themed = applyStyle(themed, "#PageIndicatorLabel", style(theme.pageIndicator(), "Center", 18, "#CBD5E1"), logger, sourceResourcePath);
            themed = applyStyle(themed, "#PrevPageButtonLabel", style(theme.paginationButton(), "Center", 16, "#E2E8F0"), logger, sourceResourcePath);
            themed = applyStyle(themed, "#NextPageButtonLabel", style(theme.paginationButton(), "Center", 16, "#E2E8F0"), logger, sourceResourcePath);
        } else {
            themed = applyStyle(themed, "#CategoryPromptLabel", style(theme.selectedCategory(), "Center", 24, "#CBD5E1"), logger, sourceResourcePath);
        }

        for (int i = 1; i <= 12; i++) {
            themed = applyStyle(themed, "#CategoryButton" + i + "Label", style(theme.categoryButton(), "Start", 18, "#FDE047"), logger, sourceResourcePath);
        }

        if (itemsTemplate) {
            for (int i = 1; i <= 5; i++) {
                themed = applyStyle(themed, "#ItemCard" + i + "Name", style(theme.itemName(), "Center", 20, "#F8FAFC"), logger, sourceResourcePath);
                themed = applyStyle(themed, "#ItemCard" + i + "Price", style(theme.itemPrice(), "Center", 18, "#FDE047"), logger, sourceResourcePath);
                themed = applyStyle(themed, "#ItemCard" + i + "Desc", style(theme.itemDescription(), "Start", 13, "#CBD5E1"), logger, sourceResourcePath);
                themed = applyStyle(themed, "#ItemCard" + i + "BuyLabel", style(theme.buyLabel(), "Center", 16, "#E2E8F0"), logger, sourceResourcePath);
            }
        }

        Path outputPath = Path.of(diskOutputPath);
        atomicWrite(outputPath, themed);

        String verify = Files.readString(outputPath, StandardCharsets.UTF_8);
        boolean containsTitleColor = verify.contains("#TitleLabel") && verify.contains("TextColor: " + theme.title().color());
        boolean containsTitleSize = verify.contains("#TitleLabel") && verify.contains("FontSize: " + theme.title().fontSize());
        logger.info("[CreditSystem] Applied theme: TitleLabel => FontSize=" + theme.title().fontSize()
                + " TextColor=" + theme.title().color() + " (verifiedInFile color=" + containsTitleColor
                + ", size=" + containsTitleSize + ")");
    }

    private static void atomicWrite(Path outputPath, String content) throws IOException {
        Files.createDirectories(outputPath.getParent());
        Path tempPath = outputPath.resolveSibling(outputPath.getFileName() + ".tmp");
        Files.writeString(tempPath, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(tempPath, outputPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String loadTemplate(String resourcePath) throws IOException {
        try (InputStream in = UiTemplateWriter.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String applyStyle(String content, String selector, String styleString, Logger logger, String sourcePath) {
        Pattern pattern = Pattern.compile("(" + Pattern.quote(selector) + "\\s*\\{[\\s\\S]*?Style:\\s*\\()([^)]*)(\\);)");
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            logger.warning("[CreditSystem] Could not find selector in template " + sourcePath + ": " + selector);
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
