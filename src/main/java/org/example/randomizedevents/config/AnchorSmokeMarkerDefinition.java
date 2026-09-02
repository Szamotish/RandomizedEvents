package org.example.randomizedevents.config;

import org.bukkit.Material;
import org.bukkit.Particle;

public record AnchorSmokeMarkerDefinition(
        boolean enabled,
        Particle particle,
        Material sourceBlock,
        int sourceRadius,
        int height,
        int intervalSeconds,
        int points,
        int count,
        double spread
) {
}
