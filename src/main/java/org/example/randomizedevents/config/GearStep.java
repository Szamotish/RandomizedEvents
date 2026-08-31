package org.example.randomizedevents.config;

import org.bukkit.inventory.ItemStack;

public record GearStep(
        int stage,
        int weight,
        ItemStack item
) {
}
