package org.example.randomizedevents.config;

public record EventPoolEntry(
        String mobClassId,
        int weight,
        int minPicks,
        int maxPicks,
        double costMultiplier
) {
}
