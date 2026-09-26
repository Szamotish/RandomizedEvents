package org.example.randomizedevents.data;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class EventSelectionState {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Long> lastTraderBoostCycles = new HashMap<>();
    private int consecutiveNonBossEvents;

    public EventSelectionState(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "event-selection-state.yml");
        load();
    }

    public int getConsecutiveNonBossEvents() {
        return consecutiveNonBossEvents;
    }

    public void recordBossEvent() {
        consecutiveNonBossEvents = 0;
        save();
    }

    public void recordNonBossEvent() {
        consecutiveNonBossEvents++;
        save();
    }

    public boolean consumeTraderBoostIfDue(World world, int intervalDays) {
        if (world == null || intervalDays <= 0) {
            return false;
        }
        long elapsedDays = Math.max(0L, world.getFullTime() / 24000L);
        long currentCycle = elapsedDays / intervalDays;
        if (currentCycle <= 0L) {
            return false;
        }
        long lastCycle = lastTraderBoostCycles.getOrDefault(world.getUID(), currentCycle - 1L);
        if (currentCycle <= lastCycle) {
            return false;
        }
        lastTraderBoostCycles.put(world.getUID(), currentCycle);
        save();
        return true;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        FileConfiguration data = YamlConfiguration.loadConfiguration(file);
        consecutiveNonBossEvents = Math.max(0, data.getInt("consecutive-non-boss-events", 0));
        ConfigurationSection worlds = data.getConfigurationSection("trader-boost-cycles");
        if (worlds == null) {
            return;
        }
        for (String rawUuid : worlds.getKeys(false)) {
            try {
                lastTraderBoostCycles.put(UUID.fromString(rawUuid), Math.max(0L, worlds.getLong(rawUuid)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid world UUID in event-selection-state.yml: " + rawUuid);
            }
        }
    }

    private void save() {
        FileConfiguration data = new YamlConfiguration();
        data.set("consecutive-non-boss-events", consecutiveNonBossEvents);
        for (Map.Entry<UUID, Long> entry : lastTraderBoostCycles.entrySet()) {
            data.set("trader-boost-cycles." + entry.getKey(), entry.getValue());
        }
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save event-selection-state.yml.", ex);
        }
    }
}
