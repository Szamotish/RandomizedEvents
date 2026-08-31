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
import org.example.randomizedevents.config.EventConfigManager;
import org.example.randomizedevents.config.EventDefinition;
import org.example.randomizedevents.mobs.EventMobRegistry;
import org.example.randomizedevents.spawn.EventSpawner;
import org.example.randomizedevents.spawn.SpawnResult;

import java.io.File;
import java.io.IOException;
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
            removeBanner(activeEvent);
        }
        activeEvents.clear();
        save();
    }

    public int removeAllActiveEvents() {
        int removed = activeEvents.size();
        for (ActiveAnchorEvent activeEvent : List.copyOf(activeEvents.values())) {
            removeBanner(activeEvent);
            mobRegistry.removeEventMobsByInstanceId(activeEvent.eventInstanceId());
            activeEvents.remove(activeEvent.eventInstanceId());
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

        long now = System.currentTimeMillis();
        activeEvents.put(eventInstanceId, new ActiveAnchorEvent(
                event.id(),
                eventInstanceId,
                bannerLocation,
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
                tickSubEvents(activeEvent, anchor, now);
                leashSleepingGuards(activeEvent, anchor);
            }
        }
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
        removeBanner(activeEvent);
        int removed = mobRegistry.removeEventMobsByInstanceId(activeEvent.eventInstanceId());
        activeEvents.remove(activeEvent.eventInstanceId());
        save();
        plugin.getLogger().info("Removed anchor event '" + activeEvent.eventId() + "' with " + removed + " mob(s).");
    }

    private void removeBanner(ActiveAnchorEvent activeEvent) {
        Block block = activeEvent.bannerLocation().getBlock();
        if (block.getType().name().endsWith("_BANNER")) {
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

    private record ActiveAnchorEvent(
            String eventId,
            String eventInstanceId,
            Location bannerLocation,
            long nextSubEventAt,
            long noTargetSince,
            long cleanupAt
    ) {
        boolean bannerDestroyed() {
            return cleanupAt > 0L;
        }

        ActiveAnchorEvent withNextSubEventAt(long value) {
            return new ActiveAnchorEvent(eventId, eventInstanceId, bannerLocation, value, noTargetSince, cleanupAt);
        }

        ActiveAnchorEvent withNoTargetSince(long value) {
            return new ActiveAnchorEvent(eventId, eventInstanceId, bannerLocation, nextSubEventAt, value, cleanupAt);
        }

        ActiveAnchorEvent withBannerDestroyed(long cleanupAt) {
            return new ActiveAnchorEvent(eventId, eventInstanceId, bannerLocation, nextSubEventAt, noTargetSince, cleanupAt);
        }
    }
}
