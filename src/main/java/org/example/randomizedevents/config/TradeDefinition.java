package org.example.randomizedevents.config;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record TradeDefinition(
        ItemStack result,
        List<ItemStack> ingredients,
        int maxUses
) {
}
