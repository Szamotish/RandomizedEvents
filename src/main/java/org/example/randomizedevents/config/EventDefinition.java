package org.example.randomizedevents.config;

import org.bukkit.block.Biome;

import java.util.List;
import java.util.Set;

public record EventDefinition(
        String id,
        String displayName,
        boolean enabled,
        double weight,
        double weightChangePerWorldStage,
        double maxWeightChange,
        int minDistance,
        int maxDistance,
        int spreadRadius,
        int announcementRadius,
        String announcementMessage,
        String targetMessage,
        boolean lightningMarker,
        SpawnMode spawnMode,
        Set<Biome> biomes,
        int budgetMin,
        int budgetMax,
        double addChanceStart,
        double addChanceDecay,
        int maxPicks,
        double customLootChance,
        List<EventPoolEntry> required,
        List<EventPoolEntry> pool,
        List<LootDefinition> loot
) {
}
