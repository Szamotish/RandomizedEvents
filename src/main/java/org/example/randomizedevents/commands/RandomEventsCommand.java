package org.example.randomizedevents.commands;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.example.randomizedevents.RandomizedEvents;
import org.example.randomizedevents.config.EventConfigManager;
import org.example.randomizedevents.config.EventDefinition;
import org.example.randomizedevents.config.MobDefinition;
import org.example.randomizedevents.data.BossKillScoreboardService;
import org.example.randomizedevents.data.BossKillTracker;
import org.example.randomizedevents.events.ActiveAnchorEventService;
import org.example.randomizedevents.mobs.EventMobRegistry;
import org.example.randomizedevents.spawn.EventScheduler;
import org.example.randomizedevents.spawn.EventSpawner;
import org.example.randomizedevents.spawn.SpawnResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RandomEventsCommand implements CommandExecutor, TabCompleter {

    private final RandomizedEvents plugin;
    private final EventConfigManager config;
    private final EventScheduler scheduler;
    private final EventSpawner spawner;
    private final EventMobRegistry mobRegistry;
    private final BossKillScoreboardService bossKillScoreboardService;
    private final BossKillTracker bossKillTracker;
    private final ActiveAnchorEventService activeAnchorEventService;

    public RandomEventsCommand(RandomizedEvents plugin, EventConfigManager config, EventScheduler scheduler,
                               EventSpawner spawner, EventMobRegistry mobRegistry,
                               BossKillScoreboardService bossKillScoreboardService, BossKillTracker bossKillTracker,
                               ActiveAnchorEventService activeAnchorEventService) {
        this.plugin = plugin;
        this.config = config;
        this.scheduler = scheduler;
        this.spawner = spawner;
        this.mobRegistry = mobRegistry;
        this.bossKillScoreboardService = bossKillScoreboardService;
        this.bossKillTracker = bossKillTracker;
        this.activeAnchorEventService = activeAnchorEventService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && isPublicSubcommand(args[0])) {
            bossBoard(sender, args);
            return true;
        }

        if (!sender.hasPermission("randomizedevents.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> reload(sender);
            case "status" -> status(sender);
            case "start" -> start(sender);
            case "stop" -> stop(sender);
            case "run" -> run(sender, args);
            case "spawnclass" -> spawnClass(sender, args);
            case "gearstage" -> gearStage(sender, args);
            case "setstage" -> setStage(sender, args);
            case "list" -> list(sender, args);
            case "cleanup" -> cleanup(sender);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /" + label + " for help.");
                return true;
            }
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "RandomizedEvents admin commands:");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " reload" + ChatColor.WHITE + " - Reload config");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " status" + ChatColor.WHITE + " - Show scheduler status");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " start" + ChatColor.WHITE + " - Enable runtime spawning");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " stop" + ChatColor.WHITE + " - Disable runtime spawning");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " run [event] [player]" + ChatColor.WHITE + " - Force an event");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " spawnclass <mob_class> <player>" + ChatColor.WHITE + " - Spawn one mob class");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " gearstage [player]" + ChatColor.WHITE + " - Show scaling stages");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " setstage <gear|world|both> <stage|auto>" + ChatColor.WHITE + " - Override scaling stage");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " list <events|mobs>" + ChatColor.WHITE + " - List configured ids");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " cleanup" + ChatColor.WHITE + " - Remove event mobs");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " bossboard [on|off|toggle]" + ChatColor.WHITE + " - Toggle your boss kill board");
    }

    private void reload(CommandSender sender) {
        scheduler.reload();
        if (config.isCleanupOnReload()) {
            activeAnchorEventService.removeAllActiveEvents();
        }
        bossKillScoreboardService.refreshAll();
        sender.sendMessage(ChatColor.GREEN + "RandomizedEvents config reloaded.");
    }

    private void status(CommandSender sender) {
        Duration next = scheduler.timeUntilNextEvent();
        sender.sendMessage(ChatColor.YELLOW + "RandomizedEvents status:");
        sender.sendMessage(ChatColor.GRAY + "Runtime enabled: " + ChatColor.WHITE + config.isEnabled());
        sender.sendMessage(ChatColor.GRAY + "Scheduler running: " + ChatColor.WHITE + scheduler.isRunning());
        sender.sendMessage(ChatColor.GRAY + "Loaded mob classes: " + ChatColor.WHITE + config.getMobClasses().size());
        sender.sendMessage(ChatColor.GRAY + "Loaded gear profiles: " + ChatColor.WHITE + config.getGearProfiles().size());
        sender.sendMessage(ChatColor.GRAY + "Loaded events: " + ChatColor.WHITE + config.getEvents().size());
        sender.sendMessage(ChatColor.GRAY + "Enabled events: " + ChatColor.WHITE + config.getEnabledEvents().size());
        sender.sendMessage(ChatColor.GRAY + "Next random event: " + ChatColor.WHITE + next.toSeconds() + "s");
        if (sender instanceof Player player) {
            sender.sendMessage(ChatColor.GRAY + "Gear stage: " + ChatColor.WHITE + config.getGearScalingStage(player.getWorld()));
            sender.sendMessage(ChatColor.GRAY + "World scaling stage: " + ChatColor.WHITE + config.getWorldScalingStage(player.getWorld()));
            sender.sendMessage(ChatColor.GRAY + "Stage overrides: " + ChatColor.WHITE + stageOverrideSummary());
        }
    }

    private void start(CommandSender sender) {
        config.setRuntimeEnabled(true);
        scheduler.start();
        sender.sendMessage(ChatColor.GREEN + "RandomizedEvents runtime spawning enabled.");
    }

    private void stop(CommandSender sender) {
        config.setRuntimeEnabled(false);
        sender.sendMessage(ChatColor.YELLOW + "RandomizedEvents runtime spawning disabled.");
    }

    private void run(CommandSender sender, String[] args) {
        SpawnResult result;
        if (args.length >= 3) {
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player is not online: " + args[2]);
                return;
            }
            result = spawner.spawnEvent(args[1], target);
        } else {
            result = args.length >= 2 ? spawner.spawnEvent(args[1]) : scheduler.triggerNow();
        }
        if (!result.success()) {
            sender.sendMessage(ChatColor.RED + "Could not start event: " + result.reason());
            return;
        }

        String message = config.message("forced-event-started")
                .replace("{event}", result.event().displayName())
                .replace("{player}", result.targetPlayerName());
        sender.sendMessage(message + ChatColor.GRAY + " Spawned mobs: " + result.spawnedMobs());
        plugin.getLogger().info(sender.getName() + " forced event '" + result.event().id() + "'.");
    }

    private void spawnClass(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /randomevents spawnclass <mob_class> <player>");
            return;
        }
        MobDefinition mobClass = config.getMobClass(args[1]);
        if (mobClass == null) {
            sender.sendMessage(ChatColor.RED + "Unknown mob class: " + args[1]);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player is not online: " + args[2]);
            return;
        }

        int spawned = spawner.spawnMobClassAt(mobClass, target.getLocation(), 3, target, "debug_spawnclass");
        sender.sendMessage(ChatColor.GREEN + "Spawned " + spawned + " mob(s) from class " + ChatColor.YELLOW + mobClass.id()
                + ChatColor.GREEN + " near " + ChatColor.YELLOW + target.getName() + ChatColor.GREEN + ".");
    }

    private void gearStage(CommandSender sender, String[] args) {
        Player target = null;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player is not online: " + args[1]);
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        }

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Usage from console: /randomevents gearstage <player>");
            return;
        }

        World world = target.getWorld();
        long days = Math.max(0L, world.getFullTime() / 24000L);
        sender.sendMessage(ChatColor.YELLOW + "Scaling stages for " + target.getName() + ":");
        sender.sendMessage(ChatColor.GRAY + "World: " + ChatColor.WHITE + world.getName());
        sender.sendMessage(ChatColor.GRAY + "World day: " + ChatColor.WHITE + days);
        sender.sendMessage(ChatColor.GRAY + "Gear stage: " + ChatColor.WHITE + config.getGearScalingStage(world)
                + stageMode(config.isGearStageOverridden()));
        sender.sendMessage(ChatColor.GRAY + "World scaling stage: " + ChatColor.WHITE + config.getWorldScalingStage(world)
                + stageMode(config.isWorldStageOverridden()));
    }

    private void setStage(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /randomevents setstage <gear|world|both> <stage|auto>");
            return;
        }

        String target = args[1].toLowerCase(Locale.ROOT);
        Integer stage = parseStageOverride(args[2]);
        if (stage == null && !"auto".equalsIgnoreCase(args[2])) {
            sender.sendMessage(ChatColor.RED + "Stage must be a number or auto.");
            return;
        }

        switch (target) {
            case "gear" -> config.setGearStageOverride(stage);
            case "world" -> config.setWorldStageOverride(stage);
            case "both" -> {
                config.setGearStageOverride(stage);
                config.setWorldStageOverride(stage);
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Target must be gear, world, or both.");
                return;
            }
        }

        bossKillScoreboardService.refreshAll();
        sender.sendMessage(ChatColor.GREEN + "Stage override updated: " + ChatColor.WHITE + stageOverrideSummary());
    }

    private Integer parseStageOverride(String rawValue) {
        if ("auto".equalsIgnoreCase(rawValue)) {
            return null;
        }
        try {
            return Math.max(0, Integer.parseInt(rawValue));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stageMode(boolean overridden) {
        return overridden ? ChatColor.DARK_GRAY + " (override)" : ChatColor.DARK_GRAY + " (auto)";
    }

    private String stageOverrideSummary() {
        String gear = config.isGearStageOverridden() ? String.valueOf(config.getGearStageOverride()) : "auto";
        String world = config.isWorldStageOverridden() ? String.valueOf(config.getWorldStageOverride()) : "auto";
        return "gear=" + gear + ", world=" + world;
    }

    private void list(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /randomevents list <events|mobs>");
            return;
        }

        if ("events".equalsIgnoreCase(args[1])) {
            sender.sendMessage(ChatColor.YELLOW + "Configured events:");
            config.getEvents().values().stream()
                    .sorted((left, right) -> left.id().compareToIgnoreCase(right.id()))
                    .forEach(event -> sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + event.id()
                            + ChatColor.DARK_GRAY + " weight=" + event.weight()
                            + (event.enabled() ? "" : ChatColor.RED + " disabled")));
            return;
        }

        if ("mobs".equalsIgnoreCase(args[1])) {
            sender.sendMessage(ChatColor.YELLOW + "Configured mob classes:");
            config.getMobClasses().values().stream()
                    .sorted((left, right) -> left.id().compareToIgnoreCase(right.id()))
                    .forEach(mob -> sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + mob.id()
                            + ChatColor.DARK_GRAY + " type=" + mob.type().name()
                            + " cost=" + mob.cost()));
            return;
        }

        sender.sendMessage(ChatColor.RED + "Unknown list type. Use events or mobs.");
    }

    private void bossBoard(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use the boss kill scoreboard.");
            return;
        }

        String mode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "toggle";
        switch (mode) {
            case "on" -> {
                bossKillScoreboardService.setEnabled(player, true);
                sendBossBoardStatus(player, true);
            }
            case "off" -> {
                bossKillScoreboardService.setEnabled(player, false);
                sendBossBoardStatus(player, false);
            }
            case "toggle" -> sendBossBoardStatus(player, bossKillScoreboardService.toggle(player));
            default -> player.sendMessage(ChatColor.RED + "Usage: /randomevents bossboard [on|off|toggle]");
        }
    }

    private void sendBossBoardStatus(Player player, boolean enabled) {
        int totalKills = bossKillTracker.getTotalKills(player);
        player.sendMessage((enabled ? ChatColor.GREEN + "Boss kill scoreboard enabled." : ChatColor.YELLOW + "Boss kill scoreboard disabled.")
                + ChatColor.GRAY + " Total boss kills: " + ChatColor.WHITE + totalKills);
    }

    private void cleanup(CommandSender sender) {
        int anchors = activeAnchorEventService.removeAllActiveEvents();
        int removed = mobRegistry.removeAllEventMobs();
        sender.sendMessage(ChatColor.GREEN + "Removed " + removed + " event mob(s) and " + anchors + " active anchor event(s).");
    }

    private boolean isPublicSubcommand(String subcommand) {
        return "bossboard".equalsIgnoreCase(subcommand)
                || "bosskills".equalsIgnoreCase(subcommand)
                || "scoreboard".equalsIgnoreCase(subcommand);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("bossboard", "bosskills", "scoreboard"));
            if (sender.hasPermission("randomizedevents.admin")) {
                values.addAll(List.of("reload", "status", "start", "stop", "run", "spawnclass", "gearstage", "setstage", "list", "cleanup"));
            }
            return partial(args[0], values);
        }
        if (args.length == 2 && isPublicSubcommand(args[0])) {
            return partial(args[1], List.of("toggle", "on", "off"));
        }
        if (!sender.hasPermission("randomizedevents.admin")) {
            return List.of();
        }
        if (args.length == 2 && "run".equalsIgnoreCase(args[0])) {
            return partial(args[1], new ArrayList<>(config.getEvents().values().stream()
                    .map(EventDefinition::id)
                    .toList()));
        }
        if (args.length == 3 && "run".equalsIgnoreCase(args[0])) {
            return partial(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 2 && "spawnclass".equalsIgnoreCase(args[0])) {
            return partial(args[1], new ArrayList<>(config.getMobClasses().values().stream()
                    .map(MobDefinition::id)
                    .toList()));
        }
        if (args.length == 3 && "spawnclass".equalsIgnoreCase(args[0])) {
            return partial(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 2 && "gearstage".equalsIgnoreCase(args[0])) {
            return partial(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 2 && "setstage".equalsIgnoreCase(args[0])) {
            return partial(args[1], List.of("gear", "world", "both"));
        }
        if (args.length == 3 && "setstage".equalsIgnoreCase(args[0])) {
            return partial(args[2], List.of("auto", "0", "4", "8", "12", "16", "20", "24"));
        }
        if (args.length == 2 && "list".equalsIgnoreCase(args[0])) {
            return partial(args[1], List.of("events", "mobs"));
        }
        return List.of();
    }

    private List<String> partial(String typed, List<String> values) {
        String lower = typed.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .toList();
    }
}
