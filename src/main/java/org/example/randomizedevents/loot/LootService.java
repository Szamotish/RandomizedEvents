package org.example.randomizedevents.loot;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.example.randomizedevents.config.EventConfigManager;
import org.example.randomizedevents.config.EventDefinition;
import org.example.randomizedevents.config.LootDefinition;
import org.example.randomizedevents.config.MobDefinition;
import org.example.randomizedevents.data.BossKillScoreboardService;
import org.example.randomizedevents.data.BossKillTracker;
import org.example.randomizedevents.mobs.EventMobRegistry;

import java.util.List;
import java.util.Random;

public final class LootService {

    private final EventConfigManager config;
    private final EventMobRegistry mobRegistry;
    private final TokenService tokenService;
    private final BossKillTracker bossKillTracker;
    private final BossKillScoreboardService bossKillScoreboardService;
    private final Random random = new Random();

    public LootService(EventConfigManager config, EventMobRegistry mobRegistry, TokenService tokenService,
                       BossKillTracker bossKillTracker, BossKillScoreboardService bossKillScoreboardService) {
        this.config = config;
        this.mobRegistry = mobRegistry;
        this.tokenService = tokenService;
        this.bossKillTracker = bossKillTracker;
        this.bossKillScoreboardService = bossKillScoreboardService;
    }

    public void dropCustomLoot(LivingEntity entity, EventDefinition event) {
        MobDefinition mobClass = config.getMobClass(mobRegistry.getMobClassId(entity));
        if (mobClass == null) {
            return;
        }

        boolean hasClassLoot = !mobClass.loot().isEmpty();
        dropLootTable(entity, mobClass.loot());
        dropBossToken(entity, mobClass);
        recordBossKill(entity, mobClass);

        if (!hasClassLoot && event != null && random.nextDouble() <= event.customLootChance()) {
            dropLootTable(entity, event.loot());
        }
    }

    private boolean dropLootTable(LivingEntity entity, List<LootDefinition> lootTable) {
        if (lootTable.isEmpty()) {
            return false;
        }

        boolean dropped = false;
        Location location = entity.getLocation();
        for (LootDefinition loot : lootTable) {
            if (random.nextDouble() > scaledLootChance(entity, loot)) {
                continue;
            }
            ItemStack item = loot.item().clone();
            item.setAmount(scaledLootAmount(entity, loot));
            location.getWorld().dropItemNaturally(location, item);
            dropped = true;
        }
        return dropped;
    }

    private double scaledLootChance(LivingEntity entity, LootDefinition loot) {
        if (!loot.worldScaling()) {
            return loot.chance();
        }
        return Math.min(1.0, loot.chance() * config.getLootChanceMultiplier(entity.getWorld()));
    }

    private int scaledLootAmount(LivingEntity entity, LootDefinition loot) {
        int amount = randomBetween(loot.minAmount(), loot.maxAmount());
        if (!loot.worldScaling()) {
            return amount;
        }
        double extraChance = config.getLootExtraAmountChance(entity.getWorld());
        for (int i = 0; i < config.getMaxLootExtraAmount(); i++) {
            if (random.nextDouble() <= extraChance) {
                amount++;
            }
        }
        return amount;
    }

    private void dropBossToken(LivingEntity entity, MobDefinition mobClass) {
        if (mobClass.bossTokenId() == null || mobClass.bossTokenName() == null) {
            return;
        }
        ItemStack token = tokenService.createBossToken(mobClass.bossTokenId(), config.color(mobClass.bossTokenName()));
        entity.getWorld().dropItemNaturally(entity.getLocation(), token);
    }

    private void recordBossKill(LivingEntity entity, MobDefinition mobClass) {
        if (mobClass.bossTokenId() == null) {
            return;
        }
        Player killer = entity.getKiller();
        if (killer != null) {
            bossKillTracker.recordKill(killer, mobClass.bossTokenId());
            if (bossKillScoreboardService.isEnabled(killer)) {
                bossKillScoreboardService.update(killer);
            }
        }
    }

    public boolean isEventMob(LivingEntity entity) {
        return mobRegistry.isEventMob(entity);
    }

    private int randomBetween(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }
}
