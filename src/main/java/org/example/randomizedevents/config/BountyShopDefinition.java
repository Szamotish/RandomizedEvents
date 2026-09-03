package org.example.randomizedevents.config;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

public record BountyShopDefinition(
        boolean enabled,
        String worldName,
        int searchRadius,
        int armDelaySeconds,
        int retryIntervalSeconds,
        int maxActivePerTarget,
        EntityType shopkeeperType,
        String shopkeeperName,
        boolean buildStructure,
        Material currencyFallbackIcon,
        List<BountyOrderDefinition> orders
) {
}
