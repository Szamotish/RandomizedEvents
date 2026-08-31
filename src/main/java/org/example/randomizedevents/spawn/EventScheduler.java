package org.example.randomizedevents.spawn;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.example.randomizedevents.config.EventConfigManager;
import org.example.randomizedevents.events.ActiveAnchorEventService;
import org.example.randomizedevents.mobs.EventMobRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class EventScheduler {

    private final JavaPlugin plugin;
    private final EventConfigManager config;
    private final EventSpawner spawner;
    private final EventMobRegistry mobRegistry;
    private final Random random = new Random();

    private BukkitTask eventTask;
    private BukkitTask cleanupTask;
    private Instant nextEventAt;
    private ActiveAnchorEventService activeAnchorEventService;
    private final Map<UUID, Long> noPlayersNearbySince = new HashMap<>();
    private final Map<UUID, Long> targetUnavailableSince = new HashMap<>();

    public EventScheduler(JavaPlugin plugin, EventConfigManager config, EventSpawner spawner, EventMobRegistry mobRegistry) {
        this.plugin = plugin;
        this.config = config;
        this.spawner = spawner;
        this.mobRegistry = mobRegistry;
    }

    public void setActiveAnchorEventService(ActiveAnchorEventService activeAnchorEventService) {
        this.activeAnchorEventService = activeAnchorEventService;
    }

    public void start() {
        stop();
        scheduleNextEvent();
        this.eventTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickEvents, 20L, 20L);
        this.cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredMobs, 20L, 20L);
    }

    public void stop() {
        if (eventTask != null) {
            eventTask.cancel();
            eventTask = null;
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        nextEventAt = null;
    }

    public void reload() {
        config.load();
        if (config.isCleanupOnReload()) {
            int removed = mobRegistry.removeAllEventMobs();
            plugin.getLogger().info("Removed " + removed + " event mob(s) during reload.");
        }
        start();
    }

    public boolean isRunning() {
        return eventTask != null && !eventTask.isCancelled();
    }

    public Duration timeUntilNextEvent() {
        if (nextEventAt == null) {
            return Duration.ZERO;
        }
        Duration duration = Duration.between(Instant.now(), nextEventAt);
        return duration.isNegative() ? Duration.ZERO : duration;
    }

    public SpawnResult triggerNow() {
        SpawnResult result = spawner.spawnRandomEvent();
        scheduleNextEvent();
        return result;
    }

    private void tickEvents() {
        if (!config.isEnabled()) {
            return;
        }
        if (nextEventAt == null) {
            scheduleNextEvent();
            return;
        }
        if (Instant.now().isBefore(nextEventAt)) {
            return;
        }

        SpawnResult result = spawner.spawnRandomEvent();
        if (!result.success()) {
            plugin.getLogger().fine("Random event skipped: " + result.reason());
        }
        scheduleNextEvent();
    }

    private void cleanupExpiredMobs() {
        int removed = removeExpiredEventMobs();
        removed += removeMobsWithNoPlayersNearby();
        removed += removeTargetBoundMobsWithUnavailableTarget();
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " expired event mob(s).");
        }
    }

    private int removeExpiredEventMobs() {
        int maxAgeSeconds = config.getDespawnAfterSeconds();
        if (maxAgeSeconds <= 0) {
            return 0;
        }

        long maxAgeMillis = maxAgeSeconds * 1000L;
        long now = System.currentTimeMillis();
        int removed = 0;

        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!mobRegistry.isEventMob(entity) || isProtectedAnchorMob(entity)) {
                    continue;
                }
                long spawnedAt = mobRegistry.getSpawnedAt(entity);
                if (spawnedAt > 0L && now - spawnedAt >= maxAgeMillis) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    private int removeMobsWithNoPlayersNearby() {
        if (!config.isDespawnWhenNoPlayersNearby()) {
            noPlayersNearbySince.clear();
            return 0;
        }

        long now = System.currentTimeMillis();
        long delayMillis = config.getNoPlayerDespawnDelaySeconds() * 1000L;
        int removed = 0;

        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!mobRegistry.isEventMob(entity) || isProtectedAnchorMob(entity)) {
                    continue;
                }
                if (!entity.getLocation().getNearbyPlayers(config.getNoPlayerDespawnRadius()).isEmpty()) {
                    noPlayersNearbySince.remove(entity.getUniqueId());
                    continue;
                }

                long since = noPlayersNearbySince.computeIfAbsent(entity.getUniqueId(), ignored -> now);
                if (now - since >= delayMillis) {
                    entity.remove();
                    noPlayersNearbySince.remove(entity.getUniqueId());
                    removed++;
                }
            }
        }
        return removed;
    }

    private int removeTargetBoundMobsWithUnavailableTarget() {
        if (!config.isDespawnTargetBoundWhenTargetUnavailable()) {
            targetUnavailableSince.clear();
            return 0;
        }

        long now = System.currentTimeMillis();
        long delayMillis = config.getTargetUnavailableDelaySeconds() * 1000L;
        long combatGraceMillis = config.getTargetUnavailableCombatGraceSeconds() * 1000L;
        int removed = 0;

        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!mobRegistry.isEventMob(entity) || isProtectedAnchorMob(entity) || !isTargetBoundCleanupMob(entity)) {
                    continue;
                }
                String rawTargetId = mobRegistry.getTargetPlayerId(entity);
                Player target = null;
                try {
                    if (rawTargetId != null) {
                        target = Bukkit.getPlayer(UUID.fromString(rawTargetId));
                    }
                } catch (IllegalArgumentException ignored) {
                    target = null;
                }

                if (target != null && target.isOnline() && !target.isDead()) {
                    targetUnavailableSince.remove(entity.getUniqueId());
                    continue;
                }

                long since = targetUnavailableSince.computeIfAbsent(entity.getUniqueId(), ignored -> now);
                if (now - since >= delayMillis && !isInCombat(entity, now, combatGraceMillis)) {
                    entity.remove();
                    targetUnavailableSince.remove(entity.getUniqueId());
                    removed++;
                }
            }
        }
        return removed;
    }

    private boolean isTargetBoundCleanupMob(LivingEntity entity) {
        String eventId = mobRegistry.getEventId(entity);
        if (eventId != null && config.getTargetUnavailableEvents().contains(eventId)) {
            return true;
        }
        for (String behavior : config.getTargetUnavailableBehaviors()) {
            if (mobRegistry.hasBehavior(entity, behavior)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInCombat(LivingEntity entity, long now, long combatGraceMillis) {
        if (entity instanceof Mob mob && mob.getTarget() instanceof Player player && player.isOnline() && !player.isDead()) {
            return true;
        }
        long lastCombatAt = mobRegistry.getLastCombatAt(entity);
        return lastCombatAt > 0L && now - lastCombatAt < combatGraceMillis;
    }

    private boolean isProtectedAnchorMob(LivingEntity entity) {
        return activeAnchorEventService != null && activeAnchorEventService.isAnchorMobProtected(entity);
    }

    private void scheduleNextEvent() {
        int min = config.getMinIntervalSeconds();
        int max = config.getMaxIntervalSeconds();
        int seconds = max <= min ? min : min + random.nextInt(max - min + 1);
        nextEventAt = Instant.now().plusSeconds(seconds);
    }
}
