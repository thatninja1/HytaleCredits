package dev.hytalemodding.creditsystem.shop;

import dev.hytalemodding.creditsystem.config.CreditConfig;

import java.util.List;

public record ShopItem(
        String name,
        long price,
        String description,
        List<String> commands,
        ItemStyles styles
) {
    public record ItemStyles(
            CreditConfig.TextStyle name,
            CreditConfig.TextStyle price,
            CreditConfig.TextStyle description,
            CreditConfig.TextStyle buy
    ) {
    }
}
