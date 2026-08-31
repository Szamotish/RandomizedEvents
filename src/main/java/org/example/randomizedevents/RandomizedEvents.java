package org.example.randomizedevents;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.randomizedevents.commands.RandomEventsCommand;
import org.example.randomizedevents.config.EventConfigManager;
import org.example.randomizedevents.data.BossKillScoreboardService;
import org.example.randomizedevents.data.BossKillTracker;
import org.example.randomizedevents.events.ActiveAnchorEventService;
import org.example.randomizedevents.events.EventBehaviorService;
import org.example.randomizedevents.events.EventMobListener;
import org.example.randomizedevents.loot.LootService;
import org.example.randomizedevents.loot.TokenService;
import org.example.randomizedevents.mobs.EventMobRegistry;
import org.example.randomizedevents.spawn.EventScheduler;
import org.example.randomizedevents.spawn.EventSpawner;

public final class RandomizedEvents extends JavaPlugin {

    private EventConfigManager configManager;
    private EventMobRegistry mobRegistry;
    private TokenService tokenService;
    private BossKillTracker bossKillTracker;
    private BossKillScoreboardService bossKillScoreboardService;
    private LootService lootService;
    private EventSpawner eventSpawner;
    private EventScheduler eventScheduler;
    private EventBehaviorService behaviorService;
    private ActiveAnchorEventService activeAnchorEventService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.configManager = new EventConfigManager(this);
        this.configManager.load();

        this.mobRegistry = new EventMobRegistry(this);
        this.tokenService = new TokenService(this);
        this.bossKillTracker = new BossKillTracker(this);
        this.bossKillScoreboardService = new BossKillScoreboardService(this, configManager, bossKillTracker);
        this.lootService = new LootService(configManager, mobRegistry, tokenService, bossKillTracker, bossKillScoreboardService);
        this.eventSpawner = new EventSpawner(this, configManager, mobRegistry);
        this.eventScheduler = new EventScheduler(this, configManager, eventSpawner, mobRegistry);
        this.activeAnchorEventService = new ActiveAnchorEventService(this, configManager, mobRegistry, eventSpawner);
        this.eventSpawner.setActiveAnchorEventService(activeAnchorEventService);
        this.eventScheduler.setActiveAnchorEventService(activeAnchorEventService);
        this.behaviorService = new EventBehaviorService(this, configManager, mobRegistry, eventSpawner);

        Bukkit.getPluginManager().registerEvents(new EventMobListener(configManager, mobRegistry, lootService, eventSpawner), this);
        Bukkit.getPluginManager().registerEvents(bossKillScoreboardService, this);
        Bukkit.getPluginManager().registerEvents(activeAnchorEventService, this);

        RandomEventsCommand command = new RandomEventsCommand(this, configManager, eventScheduler, eventSpawner, mobRegistry,
                bossKillScoreboardService, bossKillTracker, activeAnchorEventService);
        PluginCommand randomEventsCommand = getCommand("randomevents");
        if (randomEventsCommand != null) {
            randomEventsCommand.setExecutor(command);
            randomEventsCommand.setTabCompleter(command);
        }

        eventScheduler.start();
        behaviorService.start();
        activeAnchorEventService.start();
        getLogger().info("RandomizedEvents enabled.");
    }

    @Override
    public void onDisable() {
        if (eventScheduler != null) {
            eventScheduler.stop();
        }
        if (behaviorService != null) {
            behaviorService.stop();
        }
        if (activeAnchorEventService != null) {
            activeAnchorEventService.stop();
        }
        if (configManager != null && configManager.isCleanupOnDisable()) {
            if (activeAnchorEventService != null) {
                activeAnchorEventService.removeAllActiveEvents();
            }
            int removed = mobRegistry.removeAllEventMobs();
            getLogger().info("Removed " + removed + " event mob(s).");
        }
        if (bossKillTracker != null) {
            bossKillTracker.save();
        }
        getLogger().info("RandomizedEvents disabled.");
    }

    public EventConfigManager getEventConfigManager() {
        return configManager;
    }
}
