package org.example.randomizedevents.config;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record BountyOrderDefinition(
        String id,
        String displayName,
        String eventId,
        Material icon,
        List<ItemStack> cost
) {
}
