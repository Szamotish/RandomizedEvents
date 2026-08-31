package org.example.randomizedevents.config;

import org.bukkit.Material;

import java.util.List;

public record AnchorEventDefinition(
        String eventId,
        Material bannerMaterial,
        int subEventIntervalSeconds,
        int subEventRadius,
        int noTargetTimeoutSeconds,
        int bannerDestroyedDespawnSeconds,
        int guardLeashRadius,
        int guardAwakeRadius,
        List<String> subEvents
) {
}
