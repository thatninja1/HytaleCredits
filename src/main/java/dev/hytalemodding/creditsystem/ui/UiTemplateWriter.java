package dev.hytalemodding.creditsystem.ui;

import dev.hytalemodding.creditsystem.config.CreditConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UiTemplateWriter {
    private static final String EMPTY_SOURCE_RESOURCE = "Common/UI/Custom/Pages/Credits/CreditShopEmpty.ui";
    private static final String ITEMS_SOURCE_RESOURCE = "Common/UI/Custom/Pages/Credits/CreditShopItems.ui";

    private static final String GENERATED_DIR_RESOURCE = "Pages/Credits/_generated";
    private static final String GENERATED_DIR_DISK = "Common/UI/Custom/Pages/Credits/_generated";

    private UiTemplateWriter() {
    }

    public static GeneratedTemplates writeShopUiFiles(CreditConfig config, Logger logger) {
        String hash = computeThemeHash(config);
        String emptyName = "CreditShopEmpty_" + hash + ".ui";
        String itemsName = "CreditShopItems_" + hash + ".ui";

        String emptyResourcePath = GENERATED_DIR_RESOURCE + "/" + emptyName;
        String itemsResourcePath = GENERATED_DIR_RESOURCE + "/" + itemsName;
        String emptyDiskPath = GENERATED_DIR_DISK + "/" + emptyName;
        String itemsDiskPath = GENERATED_DIR_DISK + "/" + itemsName;

        try {
            writeOne(config, logger, EMPTY_SOURCE_RESOURCE, emptyDiskPath, false);
            writeOne(config, logger, ITEMS_SOURCE_RESOURCE, itemsDiskPath, true);
            cleanupOldGeneratedFiles(logger, emptyName, itemsName);
        } catch (Exception e) {
            logger.warning("[CreditSystem] Failed writing themed UI templates: " + e.getMessage());
        }

        GeneratedTemplates templates = new GeneratedTemplates(hash, emptyResourcePath, itemsResourcePath, emptyDiskPath, itemsDiskPath);
        logger.info("[CreditSystem] Active UI templates: empty=" + templates.emptyResourcePath() + " items=" + templates.itemsResourcePath());
        return templates;
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
        themed = applyStyle(themed, "#TitleLabel", style(theme.title(), "Center", 46, "#E5E7EB"), logger, sourceResourcePath);
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

            themed = themed.replace("Anchor: (Left: -8, Width: 1028, Height: 480);", "Anchor: (Left: -8, Width: 1028, Height: 480);");
            themed = themed.replace("Anchor: (Top: 0, Left: -150, Width: 1028, Height: 406);", "Anchor: (Top: 0, Left: -150, Width: 1028, Height: 406);");
            themed = themed.replace("Anchor: (Top: 406, Left: -150, Width: 1028, Height: 50);", "Anchor: (Top: 406, Left: -150, Width: 1028, Height: 50);");
        }

        Path diskPath = Path.of(diskOutputPath);
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

    private static void cleanupOldGeneratedFiles(Logger logger, String activeEmptyFile, String activeItemsFile) {
        try {
            Path generatedDir = Path.of(GENERATED_DIR_DISK);
            if (!Files.exists(generatedDir)) {
                return;
            }
            try (var paths = Files.list(generatedDir)) {
                paths.forEach(path -> {
                    String name = path.getFileName().toString();
                    boolean staleEmpty = name.startsWith("CreditShopEmpty_") && !name.equals(activeEmptyFile);
                    boolean staleItems = name.startsWith("CreditShopItems_") && !name.equals(activeItemsFile);
                    if (staleEmpty || staleItems) {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException deleteError) {
                            logger.warning("[CreditSystem] Failed deleting stale generated UI file " + path + ": " + deleteError.getMessage());
                        }
                    }
                });
            }
        } catch (Exception listError) {
            logger.warning("[CreditSystem] Failed to clean stale generated UI files: " + listError.getMessage());
        }
    }

    private static String computeThemeHash(CreditConfig config) {
        StringBuilder b = new StringBuilder();
        b.append(config.ui().title()).append('|').append(config.currencyName()).append('|');
        CreditConfig.UiTheme t = config.ui().theme();
        b.append(t.title()).append('|').append(t.credits()).append('|').append(t.selectedCategory()).append('|')
                .append(t.categoryButton()).append('|').append(t.closeButton()).append('|').append(t.pageIndicator()).append('|')
                .append(t.paginationButton()).append('|').append(t.itemName()).append('|').append(t.itemPrice()).append('|')
                .append(t.itemDescription()).append('|').append(t.buyLabel());

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(b.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 10);
        } catch (Exception e) {
            return Integer.toHexString(b.toString().hashCode());
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

    public record GeneratedTemplates(
            String hash,
            String emptyResourcePath,
            String itemsResourcePath,
            String emptyDiskPath,
            String itemsDiskPath
    ) {
        public static GeneratedTemplates defaults() {
            return new GeneratedTemplates(
                    "builtin",
                    "Pages/Credits/CreditShopEmpty.ui",
                    "Pages/Credits/CreditShopItems.ui",
                    "Common/UI/Custom/Pages/Credits/CreditShopEmpty.ui",
                    "Common/UI/Custom/Pages/Credits/CreditShopItems.ui"
            );
        }
    }
}
