package org.example.randomizedevents.spawn;

import org.bukkit.Location;
import org.example.randomizedevents.config.EventDefinition;

public record SpawnResult(
        boolean success,
        EventDefinition event,
        String targetPlayerName,
        int spawnedMobs,
        String eventInstanceId,
        Location spawnLocation,
        String reason
) {
    public static SpawnResult failed(String reason) {
        return new SpawnResult(false, null, null, 0, null, null, reason);
    }

    public static SpawnResult success(EventDefinition event, String targetPlayerName, int spawnedMobs) {
        return success(event, targetPlayerName, spawnedMobs, null, null);
    }

    public static SpawnResult success(EventDefinition event, String targetPlayerName, int spawnedMobs,
                                      String eventInstanceId, Location spawnLocation) {
        return new SpawnResult(true, event, targetPlayerName, spawnedMobs, eventInstanceId,
                spawnLocation == null ? null : spawnLocation.clone(), null);
    }
}
