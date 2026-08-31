package org.example.randomizedevents.config;

import org.bukkit.inventory.ItemStack;

public record LootDefinition(
        ItemStack item,
        int minAmount,
        int maxAmount,
        double chance,
        boolean worldScaling
) {
}
