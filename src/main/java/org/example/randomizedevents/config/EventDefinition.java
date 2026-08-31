package org.example.randomizedevents.config;

import java.util.List;

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
