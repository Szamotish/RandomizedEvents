package org.example.randomizedevents.spawn;

import org.example.randomizedevents.config.EventDefinition;

public record SpawnResult(
        boolean success,
        EventDefinition event,
        String targetPlayerName,
        int spawnedMobs,
        String eventInstanceId,
        String reason
) {
    public static SpawnResult failed(String reason) {
        return new SpawnResult(false, null, null, 0, null, reason);
    }

    public static SpawnResult success(EventDefinition event, String targetPlayerName, int spawnedMobs) {
        return success(event, targetPlayerName, spawnedMobs, null);
    }

    public static SpawnResult success(EventDefinition event, String targetPlayerName, int spawnedMobs, String eventInstanceId) {
        return new SpawnResult(true, event, targetPlayerName, spawnedMobs, eventInstanceId, null);
    }
}
