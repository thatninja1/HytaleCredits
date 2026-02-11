package dev.hytalemodding.creditsystem.shop;

import java.util.List;

public record ShopItem(
        String name,
        long price,
        String description,
        List<String> commands
) {
}
