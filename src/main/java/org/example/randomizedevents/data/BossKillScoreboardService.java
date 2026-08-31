package org.example.randomizedevents.data;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.example.randomizedevents.config.EventConfigManager;
import org.example.randomizedevents.config.MobDefinition;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class BossKillScoreboardService implements Listener {

    private static final String OBJECTIVE_NAME = "re_boss";

    private final JavaPlugin plugin;
    private final EventConfigManager config;
    private final BossKillTracker bossKillTracker;
    private final File file;
    private final Set<UUID> enabledPlayers = new HashSet<>();
    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();

    public BossKillScoreboardService(JavaPlugin plugin, EventConfigManager config, BossKillTracker bossKillTracker) {
        this.plugin = plugin;
        this.config = config;
        this.bossKillTracker = bossKillTracker;
        this.file = new File(plugin.getDataFolder(), "boss-scoreboards.yml");
        load();
    }

    public boolean toggle(Player player) {
        if (isEnabled(player)) {
            setEnabled(player, false);
            return false;
        }
        setEnabled(player, true);
        return true;
    }

    public void setEnabled(Player player, boolean enabled) {
        if (enabled) {
            enabledPlayers.add(player.getUniqueId());
            rememberPreviousScoreboard(player);
            update(player);
        } else {
            enabledPlayers.remove(player.getUniqueId());
            clear(player);
        }
        save();
    }

    public boolean isEnabled(Player player) {
        return enabledPlayers.contains(player.getUniqueId());
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isEnabled(player)) {
                update(player);
            }
        }
    }

    public void update(Player player) {
        if (!player.isOnline()) {
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, "dummy",
                ChatColor.DARK_RED + "" + ChatColor.BOLD + "Boss Kills");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Map<String, Integer> kills = bossKillTracker.getKills(player);
        int totalKills = kills.values().stream().mapToInt(Integer::intValue).sum();
        objective.getScore(ChatColor.GOLD + "Total").setScore(totalKills);

        Map<String, String> tokenNames = bossTokenNames();
        int lineIndex = 0;
        for (Map.Entry<String, Integer> entry : kills.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> tokenNames.getOrDefault(entry.getKey(), entry.getKey())))
                .toList()) {
            String label = tokenNames.getOrDefault(entry.getKey(), entry.getKey());
            objective.getScore(uniqueLine(shorten(label), lineIndex++)).setScore(entry.getValue());
        }

        if (lineIndex == 0) {
            objective.getScore(ChatColor.GRAY + "No boss kills").setScore(0);
        }

        player.setScoreboard(scoreboard);
    }

    private Map<String, String> bossTokenNames() {
        Map<String, String> tokenNames = new HashMap<>();
        config.getMobClasses().values().stream()
                .filter(mob -> mob.bossTokenId() != null && mob.bossTokenName() != null)
                .sorted(Comparator.comparing(MobDefinition::bossTokenId))
                .forEach(mob -> tokenNames.put(mob.bossTokenId(), cleanTokenName(mob.bossTokenName())));
        return tokenNames;
    }

    private String cleanTokenName(String tokenName) {
        String stripped = ChatColor.stripColor(config.color(tokenName));
        if (stripped == null || stripped.isBlank()) {
            return "Unknown Boss";
        }
        return stripped.replaceFirst("(?i)\\s+token$", "");
    }

    private String uniqueLine(String label, int index) {
        ChatColor[] colors = ChatColor.values();
        return colors[index % colors.length] + label;
    }

    private String shorten(String value) {
        return value.length() <= 32 ? value : value.substring(0, 32);
    }

    private void clear(Player player) {
        Objective current = player.getScoreboard().getObjective(DisplaySlot.SIDEBAR);
        if (current != null && OBJECTIVE_NAME.equals(current.getName())) {
            Scoreboard previous = previousScoreboards.remove(player.getUniqueId());
            player.setScoreboard(previous == null ? Bukkit.getScoreboardManager().getMainScoreboard() : previous);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (isEnabled(event.getPlayer())) {
            Bukkit.getScheduler().runTask(plugin, () -> update(event.getPlayer()));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        previousScoreboards.remove(event.getPlayer().getUniqueId());
    }

    private void rememberPreviousScoreboard(Player player) {
        if (previousScoreboards.containsKey(player.getUniqueId()) || isOwnScoreboard(player.getScoreboard())) {
            return;
        }
        previousScoreboards.put(player.getUniqueId(), player.getScoreboard());
    }

    private boolean isOwnScoreboard(Scoreboard scoreboard) {
        Objective objective = scoreboard.getObjective(DisplaySlot.SIDEBAR);
        return objective != null && OBJECTIVE_NAME.equals(objective.getName());
    }

    private void load() {
        enabledPlayers.clear();
        if (!file.exists()) {
            return;
        }

        FileConfiguration data = YamlConfiguration.loadConfiguration(file);
        for (String rawUuid : data.getStringList("enabled-players")) {
            try {
                enabledPlayers.add(UUID.fromString(rawUuid));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in boss-scoreboards.yml: " + rawUuid);
            }
        }
    }

    private void save() {
        FileConfiguration data = new YamlConfiguration();
        data.set("enabled-players", enabledPlayers.stream()
                .map(UUID::toString)
                .sorted()
                .toList());

        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save boss-scoreboards.yml.", ex);
        }
    }
}
