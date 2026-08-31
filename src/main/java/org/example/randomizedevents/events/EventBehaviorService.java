package org.example.randomizedevents.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.example.randomizedevents.config.EventConfigManager;
import org.example.randomizedevents.config.MobDefinition;
import org.example.randomizedevents.mobs.EventMobRegistry;
import org.example.randomizedevents.spawn.EventSpawner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class EventBehaviorService {

    private final JavaPlugin plugin;
    private final EventConfigManager config;
    private final EventMobRegistry mobRegistry;
    private final EventSpawner spawner;
    private final Random random = new Random();
    private final Map<UUID, Long> nextMedicTick = new HashMap<>();
    private final Map<UUID, Long> nextSummonTick = new HashMap<>();
    private final Map<UUID, Long> nextDebuffTick = new HashMap<>();

    private BukkitTask task;

    public EventBehaviorService(JavaPlugin plugin, EventConfigManager config, EventMobRegistry mobRegistry, EventSpawner spawner) {
        this.plugin = plugin;
        this.config = config;
        this.mobRegistry = mobRegistry;
        this.spawner = spawner;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        nextMedicTick.clear();
        nextSummonTick.clear();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!mobRegistry.isEventMob(entity) || entity.isDead()) {
                    continue;
                }
                tickHunterFocus(entity);
                MobDefinition mobClass = config.getMobClass(mobRegistry.getMobClassId(entity));
                if (mobClass == null) {
                    continue;
                }
                tickMedic(entity, mobClass, now);
                tickSummoner(entity, mobClass, now);
                tickDebuffAura(entity, mobClass, now);
            }
        }
    }

    private void tickHunterFocus(LivingEntity entity) {
        if (!mobRegistry.hasBehavior(entity, "hunter_focus") || !(entity instanceof Mob mob)) {
            return;
        }
        String rawTargetId = mobRegistry.getTargetPlayerId(entity);
        if (rawTargetId == null) {
            return;
        }
        try {
            Player target = Bukkit.getPlayer(UUID.fromString(rawTargetId));
            if (target != null && target.isOnline() && !target.isDead()) {
                mob.setTarget(target);
            }
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed stored data from older or manually edited entities.
        }
    }

    private void tickMedic(LivingEntity entity, MobDefinition mobClass, long now) {
        if (!mobRegistry.hasBehavior(entity, "medic_aura")) {
            return;
        }
        long next = nextMedicTick.getOrDefault(entity.getUniqueId(), 0L);
        if (now < next) {
            return;
        }

        double radius = mobClass.medicHealRadius() == null ? 8.0 : mobClass.medicHealRadius();
        double amount = mobClass.medicHealAmount() == null ? 4.0 : mobClass.medicHealAmount();
        for (LivingEntity nearby : entity.getLocation().getNearbyLivingEntities(radius)) {
            if (nearby.equals(entity) || !mobRegistry.isEventMob(nearby) || nearby.isDead()) {
                continue;
            }
            double maxHealth = nearby.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                    ? nearby.getHealth()
                    : nearby.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            nearby.setHealth(Math.min(maxHealth, nearby.getHealth() + amount));
        }

        int interval = mobClass.medicIntervalSeconds() == null ? 8 : Math.max(1, mobClass.medicIntervalSeconds());
        nextMedicTick.put(entity.getUniqueId(), now + interval * 1000L);
    }

    private void tickSummoner(LivingEntity entity, MobDefinition mobClass, long now) {
        if (!mobRegistry.hasBehavior(entity, "necromancer_summon") || mobClass.summonClasses().isEmpty()) {
            return;
        }
        long next = nextSummonTick.getOrDefault(entity.getUniqueId(), 0L);
        if (now < next) {
            return;
        }

        int min = mobClass.summonCountMin() == null ? 1 : Math.max(1, mobClass.summonCountMin());
        int max = mobClass.summonCountMax() == null ? min : Math.max(min, mobClass.summonCountMax());
        int count = min + random.nextInt(max - min + 1);
        double radius = mobClass.summonRadius() == null ? 5.0 : Math.max(1.0, mobClass.summonRadius());
        Player target = resolveTarget(entity);
        String eventId = mobRegistry.getEventId(entity);
        String eventInstanceId = mobRegistry.getEventInstanceId(entity);

        for (int i = 0; i < count; i++) {
            List<String> classes = mobClass.summonClasses();
            String classId = classes.get(random.nextInt(classes.size()));
            MobDefinition summoned = config.getMobClass(classId);
            if (summoned != null) {
                Location center = entity.getLocation();
                spawner.spawnMobClassAt(summoned, center, (int) Math.ceil(radius), target,
                        eventId == null ? "summoned" : eventId, eventInstanceId);
            }
        }

        int interval = mobClass.summonIntervalSeconds() == null ? 20 : Math.max(1, mobClass.summonIntervalSeconds());
        nextSummonTick.put(entity.getUniqueId(), now + interval * 1000L);
    }

    private void tickDebuffAura(LivingEntity entity, MobDefinition mobClass, long now) {
        if (!mobRegistry.hasBehavior(entity, "debuff_aura") || mobClass.debuffEffect() == null) {
            return;
        }
        long next = nextDebuffTick.getOrDefault(entity.getUniqueId(), 0L);
        if (now < next) {
            return;
        }

        PotionEffectType effectType = PotionEffectType.getByName(mobClass.debuffEffect().toUpperCase());
        if (effectType == null) {
            return;
        }

        double radius = mobClass.debuffRadius() == null ? 10.0 : Math.max(1.0, mobClass.debuffRadius());
        int durationSeconds = mobClass.debuffDurationSeconds() == null ? 4 : Math.max(1, mobClass.debuffDurationSeconds());
        int amplifier = mobClass.debuffAmplifier() == null ? 0 : Math.max(0, mobClass.debuffAmplifier());
        PotionEffect effect = new PotionEffect(effectType, durationSeconds * 20, amplifier, true, true, true);

        for (Player player : entity.getLocation().getNearbyPlayers(radius)) {
            if (!player.isDead()) {
                player.addPotionEffect(effect);
            }
        }

        int interval = mobClass.debuffIntervalSeconds() == null ? 10 : Math.max(1, mobClass.debuffIntervalSeconds());
        nextDebuffTick.put(entity.getUniqueId(), now + interval * 1000L);
    }

    private Player resolveTarget(LivingEntity entity) {
        if (entity instanceof Mob mob && mob.getTarget() instanceof Player player) {
            return player;
        }
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : entity.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(entity.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }
}
