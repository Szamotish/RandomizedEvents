package org.example.randomizedevents.config;

import java.util.List;

public record GearSlotDefinition(
        double chance,
        List<GearStep> steps
) {
}
