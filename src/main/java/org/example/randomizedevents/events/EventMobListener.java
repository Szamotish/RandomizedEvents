package org.example.randomizedevents.events;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.example.randomizedevents.config.EventConfigManager;
import org.example.randomizedevents.config.EventDefinition;
import org.example.randomizedevents.config.MobDefinition;
import org.example.randomizedevents.loot.LootService;
import org.example.randomizedevents.mobs.EventMobRegistry;
import org.example.randomizedevents.spawn.EventSpawner;
import org.example.randomizedevents.spawn.SpawnResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class EventMobListener implements Listener {

    private final EventConfigManager config;
    private final EventMobRegistry mobRegistry;
    private final LootService lootService;
    private final EventSpawner spawner;
    private final Random random = new Random();
    private final Set<UUID> triggeredTraderAmbushes = new HashSet<>();
    private final Map<String, TraderAmbush> activeTraderAmbushes = new HashMap<>();

    private static final String TRADER_AMBUSH_TARGET_MESSAGE = "The trader has sold you out! The enemy is close.";
    private static final int TRADER_AMBUSH_EMERALD_DISCOUNT = 10;

    public EventMobListener(EventConfigManager config, EventMobRegistry mobRegistry, LootService lootService, EventSpawner spawner) {
        this.config = config;
        this.mobRegistry = mobRegistry;
        this.lootService = lootService;
        this.spawner = spawner;
    }

    @EventHandler
    public void onEventMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!lootService.isEventMob(entity)) {
            return;
        }

        if (config.isClearVanillaDrops()) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        String eventId = mobRegistry.getEventId(entity);
        if (eventId != null && !mobRegistry.hasBehavior(entity, "no_custom_loot")) {
            EventDefinition definition = config.getEvent(eventId);
            if (definition != null) {
                lootService.dropCustomLoot(entity, definition);
            }
        }

        completeTraderAmbushIfCleared(entity);
    }

    @EventHandler
    public void onEventCreeperExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (!mobRegistry.isEventMob(entity) || !mobRegistry.hasBehavior(entity, "no_block_damage")) {
            return;
        }
        event.blockList().clear();
        event.setYield(0.0f);
    }

    @EventHandler
    public void onEventMobDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        LivingEntity damager = resolveLivingDamager(event);
        markCombatIfEventMob(victim);
        if (damager != null) {
            markCombatIfEventMob(damager);
        }

        if (damager == null) {
            return;
        }
        if (!mobRegistry.isEventMob(damager)) {
            return;
        }

        MobDefinition mobClass = config.getMobClass(mobRegistry.getMobClassId(damager));
        if (mobClass == null) {
            return;
        }

        if (mobRegistry.hasBehavior(damager, "burning_melee")) {
            victim.setFireTicks(Math.max(victim.getFireTicks(), 100));
        }

        if (mobClass.poisonOnHitSeconds() != null && mobClass.poisonOnHitSeconds() > 0) {
            int amplifier = Math.max(0, mobClass.poisonOnHitAmplifier() == null ? 0 : mobClass.poisonOnHitAmplifier());
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, mobClass.poisonOnHitSeconds() * 20, amplifier));
        }
    }

    private LivingEntity resolveLivingDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity livingEntity) {
            return livingEntity;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof LivingEntity livingEntity) {
                return livingEntity;
            }
        }
        return null;
    }

    private void markCombatIfEventMob(LivingEntity entity) {
        if (mobRegistry.isEventMob(entity)) {
            mobRegistry.markCombat(entity);
        }
    }

    @EventHandler
    public void onEventMobTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || !(event.getTarget() instanceof Player)) {
            return;
        }
        if (!mobRegistry.isEventMob(entity)
                || (!mobRegistry.hasBehavior(entity, "assassin_cloak")
                && !mobRegistry.hasBehavior(entity, "self_effect_on_target"))) {
            return;
        }

        MobDefinition mobClass = config.getMobClass(mobRegistry.getMobClassId(entity));
        if (mobClass == null) {
            return;
        }

        if (mobRegistry.hasBehavior(entity, "assassin_cloak") && mobClass.invisibilityOnTargetSeconds() != null) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    Math.max(1, mobClass.invisibilityOnTargetSeconds()) * 20, 0, true, false, true));
        }
        applySelfEffectOnTarget(entity, mobClass);
    }

    private void applySelfEffectOnTarget(LivingEntity entity, MobDefinition mobClass) {
        if (mobClass.selfEffectOnTarget() == null || mobClass.selfEffectOnTargetDurationSeconds() == null) {
            return;
        }
        PotionEffectType effectType = PotionEffectType.getByName(mobClass.selfEffectOnTarget().toUpperCase());
        if (effectType == null) {
            return;
        }
        int amplifier = mobClass.selfEffectOnTargetAmplifier() == null ? 0 : Math.max(0, mobClass.selfEffectOnTargetAmplifier());
        entity.addPotionEffect(new PotionEffect(effectType,
                Math.max(1, mobClass.selfEffectOnTargetDurationSeconds()) * 20, amplifier, true, true, true));
    }

    @EventHandler
    public void onEventTraderInteract(PlayerInteractEntityEvent event) {
        if (!config.isEnabled()) {
            return;
        }
        if (!(event.getRightClicked() instanceof LivingEntity trader)) {
            return;
        }
        if (!mobRegistry.isEventMob(trader) || !mobRegistry.hasBehavior(trader, "trader_ambush")) {
            return;
        }
        if (triggeredTraderAmbushes.contains(trader.getUniqueId())) {
            return;
        }

        MobDefinition mobClass = config.getMobClass(mobRegistry.getMobClassId(trader));
        if (mobClass == null || mobClass.traderAmbushChance() == null) {
            return;
        }
        if (random.nextDouble() > mobClass.traderAmbushChance()) {
            return;
        }

        SpawnResult result = spawner.spawnEvent("ambush", event.getPlayer(), TRADER_AMBUSH_TARGET_MESSAGE);
        if (result.success()) {
            triggeredTraderAmbushes.add(trader.getUniqueId());
            activeTraderAmbushes.put(result.eventInstanceId(), new TraderAmbush(
                    trader.getUniqueId(),
                    event.getPlayer().getUniqueId(),
                    result.eventInstanceId()
            ));
        }
    }

    @EventHandler
    public void onTraderAmbushTargetDeath(PlayerDeathEvent event) {
        UUID targetId = event.getEntity().getUniqueId();
        for (TraderAmbush ambush : List.copyOf(activeTraderAmbushes.values())) {
            if (!ambush.targetId().equals(targetId)) {
                continue;
            }

            activeTraderAmbushes.remove(ambush.eventInstanceId());
            if (mobRegistry.countEventMobsByInstanceId(ambush.eventInstanceId(), null) <= 0) {
                continue;
            }
            mobRegistry.removeEventMobsByInstanceId(ambush.eventInstanceId());
            removeTrader(ambush.traderId());
        }
    }

    private void completeTraderAmbushIfCleared(LivingEntity deadEntity) {
        String eventInstanceId = mobRegistry.getEventInstanceId(deadEntity);
        TraderAmbush ambush = activeTraderAmbushes.get(eventInstanceId);
        if (ambush == null) {
            return;
        }
        if (mobRegistry.countEventMobsByInstanceId(eventInstanceId, deadEntity.getUniqueId()) > 0) {
            return;
        }

        activeTraderAmbushes.remove(eventInstanceId);
        applyTraderAmbushDiscount(ambush.traderId());
    }

    private void applyTraderAmbushDiscount(UUID traderId) {
        Entity entity = Bukkit.getEntity(traderId);
        if (!(entity instanceof WanderingTrader trader) || trader.isDead()) {
            return;
        }

        List<MerchantRecipe> recipes = new ArrayList<>(trader.getRecipes());
        for (MerchantRecipe recipe : recipes) {
            List<ItemStack> ingredients = recipe.getIngredients();
            if (ingredients.isEmpty() || ingredients.get(0).getType() != Material.EMERALD) {
                continue;
            }

            int discount = Math.min(TRADER_AMBUSH_EMERALD_DISCOUNT, Math.max(0, ingredients.get(0).getAmount() - 1));
            if (discount > 0) {
                recipe.setSpecialPrice(Math.min(recipe.getSpecialPrice(), -discount));
            }
        }
        trader.setRecipes(recipes);
    }

    private void removeTrader(UUID traderId) {
        Entity entity = Bukkit.getEntity(traderId);
        if (entity != null) {
            triggeredTraderAmbushes.remove(traderId);
            entity.remove();
        }
    }

    private record TraderAmbush(UUID traderId, UUID targetId, String eventInstanceId) {
    }
}
