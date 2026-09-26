package org.example.randomizedevents.mobs;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class EventMobRegistry {

    private final NamespacedKey eventMobKey;
    private final NamespacedKey eventIdKey;
    private final NamespacedKey eventInstanceIdKey;
    private final NamespacedKey mobClassIdKey;
    private final NamespacedKey behaviorsKey;
    private final NamespacedKey targetPlayerKey;
    private final NamespacedKey targetUnavailableSinceKey;
    private final NamespacedKey lastCombatAtKey;
    private final NamespacedKey spawnedAtKey;
    private final NamespacedKey eventOriginWorldKey;
    private final NamespacedKey eventOriginXKey;
    private final NamespacedKey eventOriginYKey;
    private final NamespacedKey eventOriginZKey;

    public EventMobRegistry(JavaPlugin plugin) {
        this.eventMobKey = new NamespacedKey(plugin, "event_mob");
        this.eventIdKey = new NamespacedKey(plugin, "event_id");
        this.eventInstanceIdKey = new NamespacedKey(plugin, "event_instance_id");
        this.mobClassIdKey = new NamespacedKey(plugin, "mob_class_id");
        this.behaviorsKey = new NamespacedKey(plugin, "behaviors");
        this.targetPlayerKey = new NamespacedKey(plugin, "target_player");
        this.targetUnavailableSinceKey = new NamespacedKey(plugin, "target_unavailable_since");
        this.lastCombatAtKey = new NamespacedKey(plugin, "last_combat_at");
        this.spawnedAtKey = new NamespacedKey(plugin, "spawned_at");
        this.eventOriginWorldKey = new NamespacedKey(plugin, "event_origin_world");
        this.eventOriginXKey = new NamespacedKey(plugin, "event_origin_x");
        this.eventOriginYKey = new NamespacedKey(plugin, "event_origin_y");
        this.eventOriginZKey = new NamespacedKey(plugin, "event_origin_z");
    }

    public void mark(LivingEntity entity, String eventId, String mobClassId, Iterable<String> behaviors, Player target) {
        mark(entity, eventId, null, mobClassId, behaviors, target);
    }

    public void mark(LivingEntity entity, String eventId, String eventInstanceId, String mobClassId, Iterable<String> behaviors, Player target) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(eventMobKey, PersistentDataType.INTEGER, 1);
        data.set(eventIdKey, PersistentDataType.STRING, eventId);
        if (eventInstanceId != null && !eventInstanceId.isBlank()) {
            data.set(eventInstanceIdKey, PersistentDataType.STRING, eventInstanceId);
        }
        data.set(mobClassIdKey, PersistentDataType.STRING, mobClassId);
        data.set(behaviorsKey, PersistentDataType.STRING, String.join(",", behaviors));
        if (target != null) {
            data.set(targetPlayerKey, PersistentDataType.STRING, target.getUniqueId().toString());
        }
        data.set(spawnedAtKey, PersistentDataType.LONG, System.currentTimeMillis());
    }

    public boolean isEventMob(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(eventMobKey, PersistentDataType.INTEGER);
    }

    public String getEventId(LivingEntity entity) {
        return entity.getPersistentDataContainer().get(eventIdKey, PersistentDataType.STRING);
    }

    public String getEventInstanceId(LivingEntity entity) {
        return entity.getPersistentDataContainer().get(eventInstanceIdKey, PersistentDataType.STRING);
    }

    public String getMobClassId(LivingEntity entity) {
        return entity.getPersistentDataContainer().get(mobClassIdKey, PersistentDataType.STRING);
    }

    public boolean hasBehavior(LivingEntity entity, String behavior) {
        String behaviors = entity.getPersistentDataContainer().get(behaviorsKey, PersistentDataType.STRING);
        if (behaviors == null || behaviors.isBlank()) {
            return false;
        }
        for (String value : behaviors.split(",")) {
            if (value.equalsIgnoreCase(behavior)) {
                return true;
            }
        }
        return false;
    }

    public String getTargetPlayerId(LivingEntity entity) {
        return entity.getPersistentDataContainer().get(targetPlayerKey, PersistentDataType.STRING);
    }

    public long getTargetUnavailableSince(LivingEntity entity) {
        Long value = entity.getPersistentDataContainer().get(targetUnavailableSinceKey, PersistentDataType.LONG);
        return value == null ? 0L : value;
    }

    public void setTargetUnavailableSince(LivingEntity entity, long timestamp) {
        entity.getPersistentDataContainer().set(targetUnavailableSinceKey, PersistentDataType.LONG, timestamp);
    }

    public void clearTargetUnavailableSince(LivingEntity entity) {
        entity.getPersistentDataContainer().remove(targetUnavailableSinceKey);
    }

    public void markCombat(LivingEntity entity) {
        entity.getPersistentDataContainer().set(lastCombatAtKey, PersistentDataType.LONG, System.currentTimeMillis());
    }

    public long getLastCombatAt(LivingEntity entity) {
        Long value = entity.getPersistentDataContainer().get(lastCombatAtKey, PersistentDataType.LONG);
        return value == null ? 0L : value;
    }

    public long getSpawnedAt(LivingEntity entity) {
        Long value = entity.getPersistentDataContainer().get(spawnedAtKey, PersistentDataType.LONG);
        return value == null ? 0L : value;
    }

    public void setEventOrigin(LivingEntity entity, Location origin) {
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(eventOriginWorldKey, PersistentDataType.STRING, origin.getWorld().getUID().toString());
        data.set(eventOriginXKey, PersistentDataType.INTEGER, origin.getBlockX());
        data.set(eventOriginYKey, PersistentDataType.INTEGER, origin.getBlockY());
        data.set(eventOriginZKey, PersistentDataType.INTEGER, origin.getBlockZ());
    }

    public Location getEventOrigin(LivingEntity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String rawWorldId = data.get(eventOriginWorldKey, PersistentDataType.STRING);
        Integer x = data.get(eventOriginXKey, PersistentDataType.INTEGER);
        Integer y = data.get(eventOriginYKey, PersistentDataType.INTEGER);
        Integer z = data.get(eventOriginZKey, PersistentDataType.INTEGER);
        if (rawWorldId == null || x == null || y == null || z == null) {
            return null;
        }
        try {
            World world = Bukkit.getWorld(UUID.fromString(rawWorldId));
            return world == null ? null : new Location(world, x, y, z);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public int removeAllEventMobs() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (isEventMob(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    public int removeExpiredEventMobs(int maxAgeSeconds) {
        if (maxAgeSeconds <= 0) {
            return 0;
        }

        long maxAgeMillis = maxAgeSeconds * 1000L;
        long now = System.currentTimeMillis();
        int removed = 0;

        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!isEventMob(entity)) {
                    continue;
                }
                long spawnedAt = getSpawnedAt(entity);
                if (spawnedAt > 0L && now - spawnedAt >= maxAgeMillis) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    public int removeEventMobsByInstanceId(String eventInstanceId) {
        if (eventInstanceId == null || eventInstanceId.isBlank()) {
            return 0;
        }

        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (isEventMob(entity) && eventInstanceId.equals(getEventInstanceId(entity))) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    public int countEventMobsByInstanceId(String eventInstanceId, UUID excludedEntityId) {
        if (eventInstanceId == null || eventInstanceId.isBlank()) {
            return 0;
        }

        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if ((excludedEntityId == null || !excludedEntityId.equals(entity.getUniqueId()))
                        && !entity.isDead()
                        && isEventMob(entity)
                        && eventInstanceId.equals(getEventInstanceId(entity))) {
                    count++;
                }
            }
        }
        return count;
    }
}
