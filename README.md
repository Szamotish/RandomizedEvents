# RandomizedEvents

Paper plugin for Minecraft `26.2` that spawns randomized hostile and non-hostile world events around players.

## Features

- Random event scheduler with configurable intervals.
- Custom event mob classes with gear, attributes, loot, and behavior flags.
- Slow world progression for gear, mob stats, event budgets, event weights, mob counts, and loot.
- Boss and unique encounters with protected boss tokens.
- Event trader with emerald and boss-token trades.
- Active enemy camp and cursed ritual anchors with banners and recurring sub-events.
- Admin debug commands for spawning events/classes and overriding progression stages.
- Optional player boss-kill scoreboard.

## Build

Requires Maven and a JDK compatible with the configured `maven.compiler.release`.

```powershell
mvn package
```

The plugin jar is built at:

```text
target/randomized_events-0.1.0.jar
```

## Install

Copy the built jar into the Paper server `plugins` directory and restart the server.

Runtime data files such as boss kills, scoreboard preferences, and active anchor events are created inside the plugin data folder.

## Commands

- `/randomevents bossboard [on|off|toggle]` - player boss-kill sidebar.
- `/randomevents reload` - reload config.
- `/randomevents status` - show plugin status.
- `/randomevents start` - enable runtime spawning.
- `/randomevents stop` - disable runtime spawning.
- `/randomevents run [event] [player]` - force an event.
- `/randomevents spawnclass <mob_class> <player>` - spawn one configured mob class.
- `/randomevents gearstage [player]` - show current gear and world scaling stages.
- `/randomevents setstage <gear|world|both> <stage|auto>` - override progression stages.
- `/randomevents list <events|mobs>` - list configured ids.
- `/randomevents cleanup` - remove event mobs and active anchor events.

Admin commands require `randomizedevents.admin`.

## Notes

The generated `target/` directory and local IDE files are ignored by Git. Build the jar locally when deploying.
