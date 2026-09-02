package org.example.randomizedevents.events;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.example.randomizedevents.config.AnchorEventDefinition;
import org.example.randomizedevents.config.AnchorSmokeMarkerDefinition;
import org.example.randomizedevents.config.EventConfigManager;
import org.example.randomizedevents.config.EventDefinition;
import org.example.randomizedevents.mobs.EventMobRegistry;
import org.example.randomizedevents.spawn.EventSpawner;
import org.example.randomizedevents.spawn.SpawnResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

public final class ActiveAnchorEventService implements Listener {

    private final JavaPlugin plugin;
    private final EventConfigManager config;
    private final EventMobRegistry mobRegistry;
    private final EventSpawner spawner;
    private final Random random = new Random();
    private final Map<String, ActiveAnchorEvent> activeEvents = new HashMap<>();
    private final Map<String, Long> nextSmokeMarkerAt = new HashMap<>();
    private final File file;

    private BukkitTask task;

    public ActiveAnchorEventService(JavaPlugin plugin, EventConfigManager config, EventMobRegistry mobRegistry, EventSpawner spawner) {
        this.plugin = plugin;
        this.config = config;
        this.mobRegistry = mobRegistry;
        this.spawner = spawner;
        this.file = new File(plugin.getDataFolder(), "active-anchor-events.yml");
        load();
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
    }

    public void removeAllBanners() {
        for (ActiveAnchorEvent activeEvent : activeEvents.values()) {
            removeSmokeSource(activeEvent);
            removeBanner(activeEvent);
        }
        activeEvents.clear();
        nextSmokeMarkerAt.clear();
        save();
    }

    public int removeAllActiveEvents() {
        int removed = activeEvents.size();
        for (ActiveAnchorEvent activeEvent : List.copyOf(activeEvents.values())) {
            removeSmokeSource(activeEvent);
            removeBanner(activeEvent);
            mobRegistry.removeEventMobsByInstanceId(activeEvent.eventInstanceId());
            activeEvents.remove(activeEvent.eventInstanceId());
            nextSmokeMarkerAt.remove(activeEvent.eventInstanceId());
        }
        save();
        return removed;
    }

    public boolean register(EventDefinition event, Location center, String eventInstanceId) {
        AnchorEventDefinition anchor = config.getAnchorEvent(event.id());
        if (anchor == null || center.getWorld() == null) {
            return false;
        }

        Location bannerLocation = center.getBlock().getLocation();
        if (!placeBanner(bannerLocation, anchor.bannerMaterial())) {
            plugin.getLogger().warning("Could not place anchor banner for event '" + event.id() + "'.");
            return false;
        }

        Location smokeSourceLocation = placeSmokeSource(bannerLocation, anchor.smokeMarker());
        long now = System.currentTimeMillis();
        activeEvents.put(eventInstanceId, new ActiveAnchorEvent(
                event.id(),
                eventInstanceId,
                bannerLocation,
                smokeSourceLocation,
                now + anchor.subEventIntervalSeconds() * 1000L,
                0L,
                0L
        ));
        save();
        return true;
    }

    public boolean isAnchorMobProtected(LivingEntity entity) {
        String eventInstanceId = mobRegistry.getEventInstanceId(entity);
        return eventInstanceId != null && activeEvents.containsKey(eventInstanceId);
    }

    @EventHandler
    public void onBannerBreak(BlockBreakEvent event) {
        ActiveAnchorEvent activeEvent = findActiveEventAt(event.getBlock().getLocation());
        if (activeEvent == null || activeEvent.bannerDestroyed()) {
            return;
        }

        AnchorEventDefinition anchor = config.getAnchorEvent(activeEvent.eventId());
        if (anchor == null) {
            return;
        }

        long cleanupAt = System.currentTimeMillis() + anchor.bannerDestroyedDespawnSeconds() * 1000L;
        activeEvents.put(activeEvent.eventInstanceId(), activeEvent.withBannerDestroyed(cleanupAt));
        save();
        event.getPlayer().sendMessage(config.inlineMessage("&7The anchor banner is down. Remaining event mobs will fade in &e"
                + anchor.bannerDestroyedDespawnSeconds() / 60 + " minutes&7."));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (ActiveAnchorEvent activeEvent : List.copyOf(activeEvents.values())) {
            AnchorEventDefinition anchor = config.getAnchorEvent(activeEvent.eventId());
            if (anchor == null) {
                removeActiveEvent(activeEvent);
                continue;
            }

            if (activeEvent.cleanupAt() > 0L && now >= activeEvent.cleanupAt()) {
                removeActiveEvent(activeEvent);
                continue;
            }

            if (!activeEvent.bannerDestroyed()) {
                if (!isBannerStillPresent(activeEvent, anchor.bannerMaterial())) {
                    activeEvents.put(activeEvent.eventInstanceId(),
                            activeEvent.withBannerDestroyed(now + anchor.bannerDestroyedDespawnSeconds() * 1000L));
                    save();
                    continue;
                }
                tickSmokeMarker(activeEvent, anchor, now);
                tickSubEvents(activeEvent, anchor, now);
                leashSleepingGuards(activeEvent, anchor);
            }
        }
    }

    private void tickSmokeMarker(ActiveAnchorEvent activeEvent, AnchorEventDefinition anchor, long now) {
        AnchorSmokeMarkerDefinition marker = anchor.smokeMarker();
        if (marker == null || !marker.enabled()) {
            nextSmokeMarkerAt.remove(activeEvent.eventInstanceId());
            return;
        }
        long nextAt = nextSmokeMarkerAt.getOrDefault(activeEvent.eventInstanceId(), 0L);
        if (now < nextAt) {
            return;
        }

        Location sourceLocation = activeEvent.smokeSourceLocation();
        if (!isSmokeSourceStillPresent(sourceLocation, marker)) {
            sourceLocation = placeSmokeSource(activeEvent.bannerLocation(), marker);
            activeEvents.put(activeEvent.eventInstanceId(), activeEvent.withSmokeSourceLocation(sourceLocation));
            save();
        }
        spawnSmokeMarker(sourceLocation == null ? activeEvent.bannerLocation() : sourceLocation, marker);
        nextSmokeMarkerAt.put(activeEvent.eventInstanceId(), now + marker.intervalSeconds() * 1000L);
    }

    private void spawnSmokeMarker(Location sourceLocation, AnchorSmokeMarkerDefinition marker) {
        World world = sourceLocation.getWorld();
        if (world == null) {
            return;
        }

        int points = Math.max(1, marker.points());
        for (int i = 0; i < points; i++) {
            double progress = points == 1 ? 1.0 : (double) i / (points - 1);
            double y = 1.0 + progress * Math.max(1, marker.height() - 1);
            Location location = sourceLocation.clone().add(
                    0.5 + randomOffset(marker.spread()),
                    y,
                    0.5 + randomOffset(marker.spread())
            );
            world.spawnParticle(marker.particle(), location, marker.count(), marker.spread(), 0.08, marker.spread(), 0.01);
        }
    }

    private double randomOffset(double spread) {
        if (spread <= 0.0) {
            return 0.0;
        }
        return (random.nextDouble() * 2.0 - 1.0) * spread;
    }

    private Location placeSmokeSource(Location bannerLocation, AnchorSmokeMarkerDefinition marker) {
        if (marker == null || !marker.enabled() || marker.sourceBlock() == null || marker.sourceRadius() <= 0) {
            return null;
        }
        World world = bannerLocation.getWorld();
        if (world == null) {
            return null;
        }

        List<Location> candidates = new ArrayList<>();
        int radius = marker.sourceRadius();
        int radiusSquared = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx == 0 && dz == 0) || dx * dx + dz * dz > radiusSquared) {
                    continue;
                }
                candidates.add(new Location(world, bannerLocation.getBlockX() + dx, bannerLocation.getBlockY(),
                        bannerLocation.getBlockZ() + dz));
            }
        }
        Collections.shuffle(candidates, random);

        for (Location candidate : candidates) {
            Location sourceLocation = findSmokeSourceLocation(world, candidate.getBlockX(), candidate.getBlockZ());
            if (sourceLocation == null || sameBlock(sourceLocation, bannerLocation)) {
                continue;
            }
            Block source = sourceLocation.getBlock();
            source.setType(marker.sourceBlock(), false);
            return sourceLocation;
        }
        plugin.getLogger().warning("Could not place smoke marker source near anchor event '" + bannerLocation + "'.");
        return null;
    }

    private Location findSmokeSourceLocation(World world, int x, int z) {
        Block topBlock = world.getHighestBlockAt(x, z);
        Block ground = topBlock.getRelative(0, -1, 0);
        Block source = ground.getRelative(0, 1, 0);
        if (!ground.getType().isSolid() || !source.getType().isAir()) {
            return null;
        }
        return source.getLocation();
    }

    private boolean isSmokeSourceStillPresent(Location location, AnchorSmokeMarkerDefinition marker) {
        return location != null
                && marker != null
                && marker.sourceBlock() != null
                && location.getBlock().getType() == marker.sourceBlock();
    }

    private void tickSubEvents(ActiveAnchorEvent activeEvent, AnchorEventDefinition anchor, long now) {
        if (now < activeEvent.nextSubEventAt()) {
            return;
        }
        if (!config.isEnabled()) {
            activeEvents.put(activeEvent.eventInstanceId(), activeEvent.withNextSubEventAt(now + anchor.subEventIntervalSeconds() * 1000L));
            save();
            return;
        }

        Player target = randomPlayerNear(activeEvent.bannerLocation(), anchor.subEventRadius());
        if (target == null) {
            long noTargetSince = activeEvent.noTargetSince() > 0L ? activeEvent.noTargetSince() : now;
            if (now - noTargetSince >= anchor.noTargetTimeoutSeconds() * 1000L) {
                removeActiveEvent(activeEvent);
            } else {
                activeEvents.put(activeEvent.eventInstanceId(), activeEvent.withNoTargetSince(noTargetSince));
                if (activeEvent.noTargetSince() == 0L) {
                    save();
                }
            }
            return;
        }

        String subEventId = anchor.subEvents().get(random.nextInt(anchor.subEvents().size()));
        SpawnResult result = spawner.spawnEvent(subEventId, target);
        if (!result.success()) {
            plugin.getLogger().fine("Anchor sub-event skipped: " + result.reason());
        }
        activeEvents.put(activeEvent.eventInstanceId(),
                activeEvent.withNextSubEventAt(now + anchor.subEventIntervalSeconds() * 1000L).withNoTargetSince(0L));
        save();
    }

    private void leashSleepingGuards(ActiveAnchorEvent activeEvent, AnchorEventDefinition anchor) {
        Location bannerLocation = activeEvent.bannerLocation();
        World world = bannerLocation.getWorld();
        if (world == null) {
            return;
        }

        double awakeRadiusSquared = anchor.guardAwakeRadius() * anchor.guardAwakeRadius();
        double leashRadiusSquared = anchor.guardLeashRadius() * anchor.guardLeashRadius();
        for (LivingEntity entity : world.getLivingEntities()) {
            if (!activeEvent.eventInstanceId().equals(mobRegistry.getEventInstanceId(entity)) || entity.isDead()) {
                continue;
            }
            if (hasNearbyPlayer(entity.getLocation(), awakeRadiusSquared) || hasPlayerTarget(entity)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(bannerLocation) > leashRadiusSquared) {
                entity.teleport(bannerLocation.clone().add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2));
            }
        }
    }

    private boolean hasPlayerTarget(LivingEntity entity) {
        return entity instanceof Mob mob && mob.getTarget() instanceof Player player && player.isOnline() && !player.isDead();
    }

    private boolean hasNearbyPlayer(Location location, double radiusSquared) {
        for (Player player : location.getWorld().getPlayers()) {
            if (!player.isDead() && player.getLocation().distanceSquared(location) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private Player randomPlayerNear(Location location, int radius) {
        if (location.getWorld() == null) {
            return null;
        }
        double radiusSquared = radius * radius;
        List<Player> candidates = location.getWorld().getPlayers().stream()
                .filter(player -> !player.isDead())
                .filter(player -> !config.isRequireTargetInSurvival()
                        || player.getGameMode() == GameMode.SURVIVAL
                        || player.getGameMode() == GameMode.ADVENTURE)
                .filter(player -> player.getLocation().distanceSquared(location) <= radiusSquared)
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private boolean placeBanner(Location location, Material material) {
        Block block = location.getBlock();
        if (!block.getType().isAir()) {
            return false;
        }
        block.setType(material, false);
        return true;
    }

    private boolean isBannerStillPresent(ActiveAnchorEvent activeEvent, Material material) {
        return activeEvent.bannerLocation().getBlock().getType() == material;
    }

    private ActiveAnchorEvent findActiveEventAt(Location location) {
        for (ActiveAnchorEvent activeEvent : activeEvents.values()) {
            if (sameBlock(activeEvent.bannerLocation(), location)) {
                return activeEvent;
            }
        }
        return null;
    }

    private boolean sameBlock(Location left, Location right) {
        return left.getWorld() != null
                && left.getWorld().equals(right.getWorld())
                && left.getBlockX() == right.getBlockX()
                && left.getBlockY() == right.getBlockY()
                && left.getBlockZ() == right.getBlockZ();
    }

    private void removeActiveEvent(ActiveAnchorEvent activeEvent) {
        removeSmokeSource(activeEvent);
        removeBanner(activeEvent);
        int removed = mobRegistry.removeEventMobsByInstanceId(activeEvent.eventInstanceId());
        activeEvents.remove(activeEvent.eventInstanceId());
        nextSmokeMarkerAt.remove(activeEvent.eventInstanceId());
        save();
        plugin.getLogger().info("Removed anchor event '" + activeEvent.eventId() + "' with " + removed + " mob(s).");
    }

    private void removeBanner(ActiveAnchorEvent activeEvent) {
        Block block = activeEvent.bannerLocation().getBlock();
        if (block.getType().name().endsWith("_BANNER")) {
            block.setType(Material.AIR, false);
        }
    }

    private void removeSmokeSource(ActiveAnchorEvent activeEvent) {
        Location location = activeEvent.smokeSourceLocation();
        if (location == null) {
            return;
        }
        Block block = location.getBlock();
        if (block.getType() == Material.CAMPFIRE || block.getType() == Material.SOUL_CAMPFIRE) {
            block.setType(Material.AIR, false);
        }
    }

    private void load() {
        activeEvents.clear();
        if (!file.exists()) {
            return;
        }

        FileConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("events");
        if (section == null) {
            return;
        }

        for (String eventInstanceId : section.getKeys(false)) {
            ConfigurationSection eventSection = section.getConfigurationSection(eventInstanceId);
            if (eventSection == null) {
                continue;
            }
            String eventId = eventSection.getString("event-id");
            World world = Bukkit.getWorld(eventSection.getString("world", ""));
            if (eventId == null || config.getAnchorEvent(eventId) == null || world == null) {
                continue;
            }

            Location bannerLocation = new Location(
                    world,
                    eventSection.getInt("x"),
                    eventSection.getInt("y"),
                    eventSection.getInt("z")
            );
            activeEvents.put(eventInstanceId, new ActiveAnchorEvent(
                    eventId,
                    eventInstanceId,
                    bannerLocation,
                    loadSmokeSourceLocation(eventSection, world),
                    eventSection.getLong("next-sub-event-at"),
                    eventSection.getLong("no-target-since"),
                    eventSection.getLong("cleanup-at")
            ));
        }
    }

    private void save() {
        FileConfiguration data = new YamlConfiguration();
        for (ActiveAnchorEvent activeEvent : activeEvents.values()) {
            String path = "events." + activeEvent.eventInstanceId();
            Location location = activeEvent.bannerLocation();
            data.set(path + ".event-id", activeEvent.eventId());
            data.set(path + ".world", location.getWorld() == null ? "" : location.getWorld().getName());
            data.set(path + ".x", location.getBlockX());
            data.set(path + ".y", location.getBlockY());
            data.set(path + ".z", location.getBlockZ());
            Location smokeSourceLocation = activeEvent.smokeSourceLocation();
            if (smokeSourceLocation != null) {
                data.set(path + ".smoke-source.x", smokeSourceLocation.getBlockX());
                data.set(path + ".smoke-source.y", smokeSourceLocation.getBlockY());
                data.set(path + ".smoke-source.z", smokeSourceLocation.getBlockZ());
            }
            data.set(path + ".next-sub-event-at", activeEvent.nextSubEventAt());
            data.set(path + ".no-target-since", activeEvent.noTargetSince());
            data.set(path + ".cleanup-at", activeEvent.cleanupAt());
        }

        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save active-anchor-events.yml.", ex);
        }
    }

    private Location loadSmokeSourceLocation(ConfigurationSection eventSection, World world) {
        ConfigurationSection sourceSection = eventSection.getConfigurationSection("smoke-source");
        if (sourceSection == null) {
            return null;
        }
        return new Location(
                world,
                sourceSection.getInt("x"),
                sourceSection.getInt("y"),
                sourceSection.getInt("z")
        );
    }

    private record ActiveAnchorEvent(
            String eventId,
            String eventInstanceId,
            Location bannerLocation,
            Location smokeSourceLocation,
            long nextSubEventAt,
            long noTargetSince,
            long cleanupAt
    ) {
        boolean bannerDestroyed() {
            return cleanupAt > 0L;
        }

        ActiveAnchorEvent withSmokeSourceLocation(Location value) {
            return new ActiveAnchorEvent(eventId, eventInstanceId, bannerLocation, value, nextSubEventAt, noTargetSince, cleanupAt);
        }

        ActiveAnchorEvent withNextSubEventAt(long value) {
            return new ActiveAnchorEvent(eventId, eventInstanceId, bannerLocation, smokeSourceLocation, value, noTargetSince, cleanupAt);
        }

        ActiveAnchorEvent withNoTargetSince(long value) {
            return new ActiveAnchorEvent(eventId, eventInstanceId, bannerLocation, smokeSourceLocation, nextSubEventAt, value, cleanupAt);
        }

        ActiveAnchorEvent withBannerDestroyed(long cleanupAt) {
            return new ActiveAnchorEvent(eventId, eventInstanceId, bannerLocation, smokeSourceLocation, nextSubEventAt, noTargetSince, cleanupAt);
        }
    }
}
