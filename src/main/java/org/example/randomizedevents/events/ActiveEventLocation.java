package org.example.randomizedevents.events;

import org.bukkit.Location;

public record ActiveEventLocation(
        String eventId,
        String eventInstanceId,
        Location location
) {
}
