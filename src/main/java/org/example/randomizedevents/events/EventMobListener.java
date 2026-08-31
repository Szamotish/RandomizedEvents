package org.example.randomizedevents.events;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.example.randomizedevents.config.EventConfigManager;
import org.example.randomizedevents.config.EventDefinition;
import org.example.randomizedevents.config.MobDefinition;
import org.example.randomizedevents.loot.LootService;
import org.example.randomizedevents.mobs.EventMobRegistry;
import org.example.randomizedevents.spawn.EventSpawner;

import java.util.HashSet;
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

        if (mobRegistry.hasBehavior(entity, "no_custom_loot")) {
            return;
        }

        String eventId = mobRegistry.getEventId(entity);
        if (eventId == null) {
            return;
        }

        EventDefinition definition = config.getEvent(eventId);
        if (definition != null) {
            lootService.dropCustomLoot(entity, definition);
        }
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

        triggeredTraderAmbushes.add(trader.getUniqueId());
        spawner.spawnEvent(config.getEvent("ambush"), event.getPlayer());
    }
}
