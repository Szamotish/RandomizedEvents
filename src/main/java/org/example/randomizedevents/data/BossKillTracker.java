package org.example.randomizedevents.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class BossKillTracker {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Map<String, Integer>> kills = new HashMap<>();

    public BossKillTracker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "boss-kills.yml");
        load();
    }

    public void recordKill(Player player, String tokenId) {
        kills.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .merge(tokenId, 1, Integer::sum);
        save();
    }

    public int getKills(Player player, String tokenId) {
        return kills.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(tokenId, 0);
    }

    public int getTotalKills(Player player) {
        return kills.getOrDefault(player.getUniqueId(), Map.of()).values().stream().mapToInt(Integer::intValue).sum();
    }

    public Map<String, Integer> getKills(Player player) {
        return Map.copyOf(kills.getOrDefault(player.getUniqueId(), Map.of()));
    }

    public void load() {
        kills.clear();
        if (!file.exists()) {
            return;
        }

        FileConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return;
        }

        for (String rawUuid : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                ConfigurationSection playerSection = players.getConfigurationSection(rawUuid);
                if (playerSection == null) {
                    continue;
                }
                Map<String, Integer> playerKills = new HashMap<>();
                for (String tokenId : playerSection.getKeys(false)) {
                    playerKills.put(tokenId, Math.max(0, playerSection.getInt(tokenId)));
                }
                kills.put(uuid, playerKills);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in boss-kills.yml: " + rawUuid);
            }
        }
    }

    public void save() {
        FileConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Integer>> playerEntry : kills.entrySet()) {
            for (Map.Entry<String, Integer> killEntry : playerEntry.getValue().entrySet()) {
                data.set("players." + playerEntry.getKey() + "." + killEntry.getKey(), killEntry.getValue());
            }
        }

        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save boss-kills.yml.", ex);
        }
    }
}
