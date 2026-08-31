package org.example.randomizedevents.mobs;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class EventMobRegistry {

    private final NamespacedKey eventMobKey;
    private final NamespacedKey eventIdKey;
    private final NamespacedKey eventInstanceIdKey;
    private final NamespacedKey mobClassIdKey;
    private final NamespacedKey behaviorsKey;
    private final NamespacedKey targetPlayerKey;
    private final NamespacedKey lastCombatAtKey;
    private final NamespacedKey spawnedAtKey;

    public EventMobRegistry(JavaPlugin plugin) {
        this.eventMobKey = new NamespacedKey(plugin, "event_mob");
        this.eventIdKey = new NamespacedKey(plugin, "event_id");
        this.eventInstanceIdKey = new NamespacedKey(plugin, "event_instance_id");
        this.mobClassIdKey = new NamespacedKey(plugin, "mob_class_id");
        this.behaviorsKey = new NamespacedKey(plugin, "behaviors");
        this.targetPlayerKey = new NamespacedKey(plugin, "target_player");
        this.lastCombatAtKey = new NamespacedKey(plugin, "last_combat_at");
        this.spawnedAtKey = new NamespacedKey(plugin, "spawned_at");
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
}
