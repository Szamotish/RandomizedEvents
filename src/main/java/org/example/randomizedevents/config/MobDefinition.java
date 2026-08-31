package org.example.randomizedevents.config;

import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.List;
import java.util.Map;

public record MobDefinition(
        String id,
        EntityType type,
        EntityType mountType,
        int minCount,
        int maxCount,
        int cost,
        String gearProfileId,
        String name,
        String announcementMessage,
        Double health,
        Double attackDamage,
        Double movementSpeed,
        Double armor,
        Double followRange,
        Double knockbackResistance,
        Double scale,
        boolean baby,
        boolean persistent,
        boolean glowing,
        boolean immuneToZombification,
        String rabbitType,
        Integer fireTicks,
        Integer creeperFuseTicks,
        Integer creeperExplosionRadius,
        Boolean creeperPowered,
        Double medicHealAmount,
        Double medicHealRadius,
        Integer medicIntervalSeconds,
        List<String> summonClasses,
        Integer summonIntervalSeconds,
        Integer summonCountMin,
        Integer summonCountMax,
        Double summonRadius,
        Integer poisonOnHitSeconds,
        Integer poisonOnHitAmplifier,
        Integer invisibilityOnTargetSeconds,
        String selfEffectOnTarget,
        Integer selfEffectOnTargetDurationSeconds,
        Integer selfEffectOnTargetAmplifier,
        String debuffEffect,
        Integer debuffDurationSeconds,
        Integer debuffAmplifier,
        Double debuffRadius,
        Integer debuffIntervalSeconds,
        Double traderAmbushChance,
        List<TradeDefinition> trades,
        String bossTokenId,
        String bossTokenName,
        List<LootDefinition> loot,
        List<String> behaviors,
        Map<EquipmentSlot, ItemStack> equipment,
        List<PotionEffect> potionEffects
) {
}
