package org.example.randomizedevents.config;

import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;

public record GearProfile(
        String id,
        Map<EquipmentSlot, GearSlotDefinition> slots
) {
}
