package org.example.randomizedevents.config;

import org.bukkit.Material;
import org.bukkit.Particle;

import java.util.List;

public record AnchorSmokeMarkerDefinition(
        boolean enabled,
        List<Particle> particles,
        Material sourceBlock,
        int sourceRadius,
        int height,
        int intervalSeconds,
        int points,
        int count,
        double spread
) {
}
