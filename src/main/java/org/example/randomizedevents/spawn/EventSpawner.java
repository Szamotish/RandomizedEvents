package org.example.randomizedevents.spawn;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.randomizedevents.config.EventConfigManager;
import org.example.randomizedevents.config.EventDefinition;
import org.example.randomizedevents.config.EventPoolEntry;
import org.example.randomizedevents.config.GearProfile;
import org.example.randomizedevents.config.GearSlotDefinition;
import org.example.randomizedevents.config.GearStep;
import org.example.randomizedevents.config.MobDefinition;
import org.example.randomizedevents.config.SpawnMode;
import org.example.randomizedevents.config.TradeDefinition;
import org.example.randomizedevents.events.ActiveAnchorEventService;
import org.example.randomizedevents.mobs.EventMobRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class EventSpawner {

    private final JavaPlugin plugin;
    private final EventConfigManager config;
    private final EventMobRegistry mobRegistry;
    private final Random random = new Random();
    private ActiveAnchorEventService activeAnchorEventService;

    public EventSpawner(JavaPlugin plugin, EventConfigManager config, EventMobRegistry mobRegistry) {
        this.plugin = plugin;
        this.config = config;
        this.mobRegistry = mobRegistry;
    }

    public void setActiveAnchorEventService(ActiveAnchorEventService activeAnchorEventService) {
        this.activeAnchorEventService = activeAnchorEventService;
    }

    public SpawnResult spawnRandomEvent() {
        List<Player> candidates = validPlayers();
        if (candidates.size() < config.getMinOnlinePlayers()) {
            return SpawnResult.failed("Not enough valid players online.");
        }

        List<EventDefinition> enabledEvents = config.getEnabledEvents();
        if (enabledEvents.isEmpty()) {
            return SpawnResult.failed("No enabled events.");
        }

        String lastFailure = "No event could be selected.";
        int attempts = Math.max(8, Math.min(24, candidates.size() * 4));
        for (int i = 0; i < attempts; i++) {
            Player target = candidates.get(random.nextInt(candidates.size()));
            EventDefinition event = weightedRandomEvent(enabledEvents, target.getWorld());
            if (event == null) {
                continue;
            }
            SpawnResult result = spawnEvent(event, target);
            if (result.success()) {
                return result;
            }
            lastFailure = result.reason();
        }

        return SpawnResult.failed(lastFailure);
    }

    public SpawnResult spawnEvent(String eventId) {
        EventDefinition event = config.getEvent(eventId);
        if (event == null || !event.enabled()) {
            return SpawnResult.failed("Event is missing or disabled: " + eventId);
        }

        List<Player> candidates = validPlayers();
        if (candidates.isEmpty()) {
            return SpawnResult.failed("No valid players online.");
        }
        Collections.shuffle(candidates, random);
        String lastFailure = "No valid spawn location.";
        for (Player target : candidates) {
            SpawnResult result = spawnEvent(event, target);
            if (result.success()) {
                return result;
            }
            lastFailure = result.reason();
        }
        return SpawnResult.failed(lastFailure);
    }

    public SpawnResult spawnEvent(String eventId, Player target) {
        return spawnEvent(eventId, target, null);
    }

    public SpawnResult spawnEvent(String eventId, Player target, String targetMessageOverride) {
        EventDefinition event = config.getEvent(eventId);
        if (event == null || !event.enabled()) {
            return SpawnResult.failed("Event is missing or disabled: " + eventId);
        }
        if (target == null || !target.isOnline() || target.isDead()) {
            return SpawnResult.failed("Target player is not valid.");
        }
        return spawnEvent(event, target, targetMessageOverride);
    }

    public SpawnResult spawnEvent(EventDefinition event, Player target) {
        return spawnEvent(event, target, null);
    }

    private SpawnResult spawnEvent(EventDefinition event, Player target, String targetMessageOverride) {
        if (event == null) {
            return SpawnResult.failed("Event is missing.");
        }
        String eventInstanceId = UUID.randomUUID().toString();
        Location center = findSpawnCenter(target, event);
        if (center == null) {
            return SpawnResult.failed("Could not find a safe spawn location near " + target.getName() + ".");
        }

        if (event.lightningMarker()) {
            center.getWorld().strikeLightningEffect(center);
        }

        int spawned = 0;
        String announcementOverride = null;
        for (EventPoolEntry required : event.required()) {
            MobDefinition mobClass = config.getMobClass(required.mobClassId());
            if (mobClass == null) {
                continue;
            }
            int picks = randomBetween(required.minPicks(), required.maxPicks());
            for (int i = 0; i < picks; i++) {
                int added = spawnMobClassAt(mobClass, center, event.spreadRadius(), target, event.id(), eventInstanceId,
                        event.spawnMode());
                if (added > 0) {
                    spawned += added;
                    announcementOverride = selectAnnouncementOverride(announcementOverride, mobClass);
                }
            }
        }

        int budget = scaledEventBudget(event, target.getWorld());
        double addChance = event.addChanceStart();
        int picks = 0;

        while (picks < event.maxPicks() && budget > 0 && random.nextDouble() <= addChance) {
            EventPoolEntry selected = weightedRandomPoolEntry(event.pool(), budget);
            if (selected == null) {
                break;
            }
            MobDefinition mobClass = config.getMobClass(selected.mobClassId());
            if (mobClass == null) {
                break;
            }

            int selectedPicks = randomBetween(selected.minPicks(), selected.maxPicks());
            for (int i = 0; i < selectedPicks && picks < event.maxPicks(); i++) {
                int cost = Math.max(1, (int) Math.ceil(mobClass.cost() * selected.costMultiplier()));
                if (cost > budget) {
                    break;
                }
                int added = spawnMobClassAt(mobClass, center, event.spreadRadius(), target, event.id(), eventInstanceId,
                        event.spawnMode());
                if (added > 0) {
                    spawned += added;
                    announcementOverride = selectAnnouncementOverride(announcementOverride, mobClass);
                    budget -= cost;
                    picks++;
                } else {
                    budget -= cost;
                }
            }

            addChance *= event.addChanceDecay();
        }

        if (spawned <= 0) {
            return SpawnResult.failed("Event selected, but no mobs could be spawned.");
        }

        registerAnchorEvent(event, center, eventInstanceId);
        announceEvent(event, target, center, announcementOverride, targetMessageOverride);
        plugin.getLogger().info("Spawned event '" + event.id() + "' near " + target.getName() + " with " + spawned + " mob(s).");
        return SpawnResult.success(event, target.getName(), spawned, eventInstanceId);
    }

    public int spawnMobClassAt(MobDefinition mobClass, Location center, int spreadRadius, Player target, String eventId) {
        return spawnMobClassAt(mobClass, center, spreadRadius, target, eventId, null);
    }

    public int spawnMobClassAt(MobDefinition mobClass, Location center, int spreadRadius, Player target, String eventId, String eventInstanceId) {
        return spawnMobClassAt(mobClass, center, spreadRadius, target, eventId, eventInstanceId, SpawnMode.LAND);
    }

    public int spawnMobClassAt(MobDefinition mobClass, Location center, int spreadRadius, Player target, String eventId,
                               String eventInstanceId, SpawnMode spawnMode) {
        int count = scaledMobCount(mobClass, center.getWorld());
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Location spawnLocation = findNearbySpawnLocation(center, spreadRadius, spawnMode);
            if (spawnLocation == null) {
                continue;
            }
            LivingEntity entity = spawnMob(spawnLocation, mobClass, target, eventId, eventInstanceId);
            if (entity != null) {
                spawned++;
            }
        }
        return spawned;
    }

    private String selectAnnouncementOverride(String current, MobDefinition mobClass) {
        if (current != null || mobClass.announcementMessage() == null || mobClass.announcementMessage().isBlank()) {
            return current;
        }
        return mobClass.announcementMessage();
    }

    private void registerAnchorEvent(EventDefinition event, Location center, String eventInstanceId) {
        if (activeAnchorEventService != null) {
            activeAnchorEventService.register(event, center, eventInstanceId);
        }
    }

    private int scaledEventBudget(EventDefinition event, World world) {
        int min = event.budgetMin();
        int max = event.budgetMax();
        if (!config.isWorldScalingEnabled() || max <= 0) {
            return randomBetween(min, max);
        }
        double multiplier = config.getEventBudgetMultiplier(world);
        int scaledMin = (int) Math.floor(min * multiplier);
        int scaledMax = (int) Math.ceil(max * multiplier);
        return randomBetween(Math.max(0, scaledMin), Math.max(scaledMin, scaledMax));
    }

    private int scaledMobCount(MobDefinition definition, World world) {
        int count = randomBetween(definition.minCount(), definition.maxCount());
        if (!shouldScaleMobCount(definition)) {
            return count;
        }
        double extraChance = config.getMobCountExtraChance(world);
        for (int i = 0; i < config.getMaxExtraMobsPerClass(); i++) {
            if (random.nextDouble() <= extraChance) {
                count++;
            }
        }
        return count;
    }

    private boolean shouldScaleMobCount(MobDefinition definition) {
        return config.isWorldScalingEnabled()
                && config.getMaxExtraMobsPerClass() > 0
                && definition.bossTokenId() == null
                && !definition.persistent()
                && !definition.behaviors().contains("trader_ambush");
    }

    private void announceEvent(EventDefinition event, Player target, Location center, String announcementOverride,
                               String targetMessageOverride) {
        if (!config.isAnnounceEvents()) {
            return;
        }

        String targetMessage = targetMessageOverride != null && !targetMessageOverride.isBlank()
                ? targetMessageOverride
                : event.targetMessage();
        boolean sentTargetMessage = targetMessage != null && !targetMessage.isBlank();
        if (sentTargetMessage) {
            target.sendMessage(formatEventMessage(config.inlineMessage(targetMessage), event, target, center));
        }

        String rawMessage = resolveAnnouncementMessage(event, announcementOverride);
        if (rawMessage == null) {
            return;
        }
        String message = formatEventMessage(rawMessage, event, target, center);

        if (event.announcementRadius() <= 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!sentTargetMessage || !player.equals(target)) {
                    player.sendMessage(message);
                }
            }
            return;
        }

        double radiusSquared = event.announcementRadius() * event.announcementRadius();
        for (Player player : center.getWorld().getPlayers()) {
            if ((!sentTargetMessage || !player.equals(target)) && player.getLocation().distanceSquared(center) <= radiusSquared) {
                player.sendMessage(message);
            }
        }
    }

    private String resolveAnnouncementMessage(EventDefinition event, String announcementOverride) {
        if (announcementOverride != null && !announcementOverride.isBlank()) {
            return config.inlineMessage(announcementOverride);
        }
        if (event.announcementMessage() != null) {
            return event.announcementMessage().isBlank() ? null : config.inlineMessage(event.announcementMessage());
        }
        return config.message("event-started");
    }

    private String formatEventMessage(String message, EventDefinition event, Player target, Location center) {
        return message
                .replace("{event}", event.displayName())
                .replace("{player}", target.getName())
                .replace("{x}", String.valueOf(center.getBlockX()))
                .replace("{y}", String.valueOf(center.getBlockY()))
                .replace("{z}", String.valueOf(center.getBlockZ()))
                .replace("{distance_min}", String.valueOf(event.minDistance()))
                .replace("{distance_max}", String.valueOf(event.maxDistance()));
    }

    private List<Player> validPlayers() {
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline() || player.isDead()) {
                continue;
            }
            if (config.isWorldDisabled(player.getWorld().getName())) {
                continue;
            }
            if (config.isRequireTargetInSurvival()
                    && player.getGameMode() != GameMode.SURVIVAL
                    && player.getGameMode() != GameMode.ADVENTURE) {
                continue;
            }
            players.add(player);
        }
        players.sort(Comparator.comparing(Player::getName));
        return players;
    }

    private EventDefinition weightedRandomEvent(List<EventDefinition> events, World world) {
        double totalWeight = events.stream().mapToDouble(event -> scaledEventWeight(event, world)).sum();
        if (totalWeight <= 0) {
            return null;
        }
        double roll = random.nextDouble(totalWeight);
        double cursor = 0.0;
        for (EventDefinition event : events) {
            cursor += scaledEventWeight(event, world);
            if (roll < cursor) {
                return event;
            }
        }
        return events.get(events.size() - 1);
    }

    private double scaledEventWeight(EventDefinition event, World world) {
        double weight = event.weight();
        if (!config.isWorldScalingEnabled() || event.maxWeightChange() <= 0.0 || event.weightChangePerWorldStage() == 0.0) {
            return weight;
        }
        double rawChange = event.weightChangePerWorldStage() * config.getWorldScalingStage(world);
        double cappedChange = rawChange >= 0.0
                ? Math.min(event.maxWeightChange(), rawChange)
                : Math.max(-event.maxWeightChange(), rawChange);
        return Math.max(0.0, weight * (1.0 + cappedChange));
    }

    private EventPoolEntry weightedRandomPoolEntry(List<EventPoolEntry> entries, int budget) {
        List<EventPoolEntry> affordable = entries.stream()
                .filter(entry -> {
                    MobDefinition mobClass = config.getMobClass(entry.mobClassId());
                    return mobClass != null && Math.ceil(mobClass.cost() * entry.costMultiplier()) <= budget;
                })
                .toList();

        int totalWeight = affordable.stream().mapToInt(EventPoolEntry::weight).sum();
        if (totalWeight <= 0) {
            return null;
        }
        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (EventPoolEntry entry : affordable) {
            cursor += entry.weight();
            if (roll < cursor) {
                return entry;
            }
        }
        return affordable.get(affordable.size() - 1);
    }

    private Location findSpawnCenter(Player target, EventDefinition event) {
        Location playerLocation = target.getLocation();
        World world = target.getWorld();

        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = randomBetween(event.minDistance(), event.maxDistance());
            int x = playerLocation.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = playerLocation.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            Location candidate = findSpawnLocation(world, x, z, event.spawnMode());
            if (candidate != null && isEventBiomeAllowed(candidate, event) && isSpawnAreaAllowed(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isEventBiomeAllowed(Location location, EventDefinition event) {
        return event.biomes().isEmpty() || event.biomes().contains(location.getBlock().getBiome());
    }

    private Location findNearbySpawnLocation(Location center, int spreadRadius) {
        return findNearbySpawnLocation(center, spreadRadius, SpawnMode.LAND);
    }

    private Location findNearbySpawnLocation(Location center, int spreadRadius, SpawnMode spawnMode) {
        if (spreadRadius <= 0) {
            return center.clone();
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = center.getBlockX() + randomBetween(-spreadRadius, spreadRadius);
            int z = center.getBlockZ() + randomBetween(-spreadRadius, spreadRadius);
            Location candidate = findSpawnLocation(center.getWorld(), x, z, spawnMode);
            if (candidate != null && candidate.distanceSquared(center) <= spreadRadius * spreadRadius + 4.0) {
                return candidate;
            }
        }
        return center.clone();
    }

    private Location findSpawnLocation(World world, int x, int z, SpawnMode spawnMode) {
        return switch (spawnMode) {
            case LAND -> findSurfaceLocation(world, x, z);
            case WATER_SURFACE -> findWaterSurfaceLocation(world, x, z);
            case UNDERWATER -> findUnderwaterLocation(world, x, z);
        };
    }

    private Location findSurfaceLocation(World world, int x, int z) {
        if (world == null) {
            return null;
        }
        if (config.isRequireLoadedChunkForSpawn() && !world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }

        Block topBlock = world.getHighestBlockAt(x, z);
        Block ground = topBlock.getRelative(0, -1, 0);
        Block body = ground.getRelative(0, 1, 0);
        Block head = ground.getRelative(0, 2, 0);

        if (config.isBiomeDisabled(ground.getBiome())) {
            return null;
        }
        if (!ground.getType().isSolid() || isUnsafeGround(ground.getType())) {
            return null;
        }
        if (!body.isPassable() || !head.isPassable() || isUnsafeSpace(body.getType()) || isUnsafeSpace(head.getType())) {
            return null;
        }

        return new Location(world, x + 0.5, body.getY(), z + 0.5);
    }

    private Location findWaterSurfaceLocation(World world, int x, int z) {
        if (world == null) {
            return null;
        }
        if (config.isRequireLoadedChunkForSpawn() && !world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }

        for (int y = world.getMaxHeight() - 1; y >= world.getMinHeight(); y--) {
            Block water = world.getBlockAt(x, y, z);
            if (water.getType() != Material.WATER) {
                continue;
            }
            Block below = water.getRelative(0, -1, 0);
            Block above = water.getRelative(0, 1, 0);
            if (below.getType() != Material.WATER || config.isBiomeDisabled(water.getBiome())) {
                return null;
            }
            if (above.getType() == Material.WATER || !above.isPassable() || isUnsafeSpace(above.getType())) {
                continue;
            }
            return new Location(world, x + 0.5, water.getY(), z + 0.5);
        }
        return null;
    }

    private Location findUnderwaterLocation(World world, int x, int z) {
        if (world == null) {
            return null;
        }
        if (config.isRequireLoadedChunkForSpawn() && !world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }

        List<Block> candidates = new ArrayList<>();
        for (int y = world.getMinHeight(); y < world.getMaxHeight() - 1; y++) {
            Block body = world.getBlockAt(x, y, z);
            Block head = body.getRelative(0, 1, 0);
            if (body.getType() == Material.WATER
                    && head.getType() == Material.WATER
                    && !config.isBiomeDisabled(body.getBiome())) {
                candidates.add(body);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        Block selected = candidates.get(random.nextInt(candidates.size()));
        return new Location(world, x + 0.5, selected.getY(), z + 0.5);
    }

    private boolean isUnsafeGround(Material material) {
        return material == Material.LAVA
                || material == Material.WATER
                || material == Material.CACTUS
                || material == Material.FIRE
                || material == Material.MAGMA_BLOCK
                || material == Material.CAMPFIRE
                || material == Material.SOUL_CAMPFIRE;
    }

    private boolean isUnsafeSpace(Material material) {
        return material == Material.WATER
                || material == Material.LAVA
                || material == Material.FIRE
                || material == Material.SOUL_FIRE
                || material == Material.POWDER_SNOW
                || material == Material.COBWEB
                || material == Material.SWEET_BERRY_BUSH;
    }

    private boolean isSpawnAreaAllowed(Location location) {
        return isFarEnoughFromWorldSpawn(location)
                && isFarEnoughFromOnlineBedSpawns(location)
                && !hasTooManyEventMobsNearby(location);
    }

    private boolean isFarEnoughFromWorldSpawn(Location location) {
        int safeRadius = config.getSafeRadiusFromWorldSpawn();
        if (safeRadius <= 0 || location.getWorld() == null) {
            return true;
        }
        return location.distanceSquared(location.getWorld().getSpawnLocation()) >= safeRadius * safeRadius;
    }

    private boolean isFarEnoughFromOnlineBedSpawns(Location location) {
        int minDistance = config.getMinDistanceFromBedSpawns();
        if (minDistance <= 0 || location.getWorld() == null) {
            return true;
        }
        double minDistanceSquared = minDistance * minDistance;
        for (Player player : location.getWorld().getPlayers()) {
            Location bedSpawn = player.getBedSpawnLocation();
            if (bedSpawn != null
                    && bedSpawn.getWorld() != null
                    && bedSpawn.getWorld().equals(location.getWorld())
                    && bedSpawn.distanceSquared(location) < minDistanceSquared) {
                return false;
            }
        }
        return true;
    }

    private boolean hasTooManyEventMobsNearby(Location location) {
        int maxMobs = config.getMaxEventMobsInRadius();
        if (maxMobs <= 0) {
            return false;
        }

        int count = 0;
        for (LivingEntity entity : location.getWorld().getNearbyLivingEntities(location, config.getEventMobDensityRadius())) {
            if (mobRegistry.isEventMob(entity) && ++count >= maxMobs) {
                return true;
            }
        }
        return false;
    }

    private LivingEntity spawnMob(Location location, MobDefinition definition, Player target, String eventId, String eventInstanceId) {
        LivingEntity mount = null;
        if (definition.mountType() != null) {
            mount = spawnLiving(location, definition.mountType());
            if (mount != null) {
                mobRegistry.mark(mount, eventId, eventInstanceId, definition.id() + "_mount", List.of("mount", "no_custom_loot", "hunter_focus"), target);
                mount.setPersistent(definition.persistent());
                mount.setRemoveWhenFarAway(!definition.persistent());
                applyNetherSafety(mount, definition);
                if (mount instanceof Mob mob && target != null) {
                    mob.setTarget(target);
                }
            }
        }

        LivingEntity entity = spawnLiving(location, definition.type());
        if (entity == null) {
            if (mount != null) {
                mount.remove();
            }
            return null;
        }

        mobRegistry.mark(entity, eventId, eventInstanceId, definition.id(), definition.behaviors(), target);
        entity.setPersistent(definition.persistent());
        entity.setRemoveWhenFarAway(!definition.persistent());
        entity.setGlowing(definition.glowing());
        entity.setCanPickupItems(false);

        if (definition.name() != null && !definition.name().isBlank()) {
            entity.setCustomName(config.color(definition.name()));
            entity.setCustomNameVisible(true);
        }

        if (definition.baby()) {
            applyBaby(entity);
        }

        if (definition.fireTicks() != null) {
            entity.setFireTicks(Math.max(1, definition.fireTicks()));
        }

        applyRabbitType(entity, definition);
        applyCreeperSettings(entity, definition);
        applyNetherSafety(entity, definition);
        applyAttributes(entity, definition, location.getWorld());
        applyEquipment(entity, resolveEquipment(location, definition));
        applyTrades(entity, definition.trades());
        definition.potionEffects().forEach(entity::addPotionEffect);

        if (entity instanceof Mob mob && target != null) {
            mob.setTarget(target);
        }
        if (mount != null) {
            mount.addPassenger(entity);
        }

        return entity;
    }

    private Map<EquipmentSlot, ItemStack> resolveEquipment(Location location, MobDefinition definition) {
        Map<EquipmentSlot, ItemStack> resolved = new EnumMap<>(EquipmentSlot.class);

        if (config.isGearScalingEnabled() && definition.gearProfileId() != null) {
            GearProfile profile = config.getGearProfile(definition.gearProfileId());
            if (profile != null) {
                int stage = currentGearStage(location.getWorld());
                for (Map.Entry<EquipmentSlot, GearSlotDefinition> entry : profile.slots().entrySet()) {
                    if (random.nextDouble() > entry.getValue().chance()) {
                        continue;
                    }
                    GearStep selected = selectGearStep(entry.getValue().steps(), stage);
                    if (selected != null) {
                        resolved.put(entry.getKey(), selected.item().clone());
                    }
                }
            }
        }

        for (Map.Entry<EquipmentSlot, ItemStack> entry : definition.equipment().entrySet()) {
            resolved.put(entry.getKey(), entry.getValue().clone());
        }

        return resolved;
    }

    private int currentGearStage(World world) {
        if (world == null) {
            return 0;
        }
        int stage = config.getGearScalingStage(world);
        if (config.isGearStageOverridden()) {
            return stage;
        }
        int variance = config.getGearStageVariance();
        if (variance > 0) {
            stage += randomBetween(-variance, variance);
        }
        return Math.max(0, Math.min(config.getMaxGearStage(), stage));
    }

    private GearStep selectGearStep(List<GearStep> steps, int stage) {
        int selectedStage = -1;
        for (GearStep step : steps) {
            if (step.stage() > stage) {
                break;
            }
            selectedStage = step.stage();
        }
        if (selectedStage < 0 && !steps.isEmpty()) {
            selectedStage = steps.getFirst().stage();
        }

        int totalWeight = 0;
        List<GearStep> candidates = new ArrayList<>();
        for (GearStep step : steps) {
            if (step.stage() == selectedStage) {
                candidates.add(step);
                totalWeight += step.weight();
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (GearStep candidate : candidates) {
            cursor += candidate.weight();
            if (roll < cursor) {
                return candidate;
            }
        }
        return candidates.getLast();
    }

    private LivingEntity spawnLiving(Location location, EntityType type) {
        Entity spawned = location.getWorld().spawnEntity(location, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
        if (!(spawned instanceof LivingEntity entity)) {
            spawned.remove();
            return null;
        }
        return entity;
    }

    private void applyBaby(LivingEntity entity) {
        if (entity instanceof PiglinAbstract piglin) {
            piglin.setBaby(true);
            return;
        }
        if (entity instanceof Ageable ageable) {
            ageable.setBaby();
            ageable.setAgeLock(true);
        }
    }

    private void applyRabbitType(LivingEntity entity, MobDefinition definition) {
        if (!(entity instanceof Rabbit rabbit) || definition.rabbitType() == null) {
            return;
        }
        try {
            rabbit.setRabbitType(Rabbit.Type.valueOf(definition.rabbitType().toUpperCase().replace('-', '_')));
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Invalid rabbit type '" + definition.rabbitType() + "' in mob class '" + definition.id() + "'.");
        }
    }

    private void applyCreeperSettings(LivingEntity entity, MobDefinition definition) {
        if (!(entity instanceof Creeper creeper)) {
            return;
        }
        if (definition.creeperFuseTicks() != null) {
            creeper.setMaxFuseTicks(Math.max(1, definition.creeperFuseTicks()));
        }
        if (definition.creeperExplosionRadius() != null) {
            creeper.setExplosionRadius(Math.max(1, definition.creeperExplosionRadius()));
        }
        if (definition.creeperPowered() != null) {
            creeper.setPowered(definition.creeperPowered());
        }
    }

    private void applyNetherSafety(LivingEntity entity, MobDefinition definition) {
        if (!definition.immuneToZombification()) {
            return;
        }
        if (entity instanceof PiglinAbstract piglin) {
            piglin.setImmuneToZombification(true);
        }
        if (entity instanceof Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        }
    }

    private void applyTrades(LivingEntity entity, List<TradeDefinition> trades) {
        if (!(entity instanceof WanderingTrader trader) || trades.isEmpty()) {
            return;
        }
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (TradeDefinition trade : trades) {
            MerchantRecipe recipe = new MerchantRecipe(trade.result(), trade.maxUses());
            trade.ingredients().forEach(recipe::addIngredient);
            recipes.add(recipe);
        }
        trader.setRecipes(recipes);
        trader.setDespawnDelay(20 * 60 * 20);
    }

    private void applyAttributes(LivingEntity entity, MobDefinition definition, World world) {
        if (definition.health() != null) {
            double health = definition.health() * config.getHealthMultiplier(world);
            AttributeInstance maxHealth = setAttribute(entity, Attribute.MAX_HEALTH, health);
            if (maxHealth != null) {
                entity.setHealth(Math.min(health, maxHealth.getValue()));
            }
        }
        if (definition.attackDamage() != null) {
            setAttribute(entity, Attribute.ATTACK_DAMAGE, definition.attackDamage() * config.getAttackDamageMultiplier(world));
        }
        if (definition.movementSpeed() != null) {
            setAttribute(entity, Attribute.MOVEMENT_SPEED, definition.movementSpeed() * config.getMovementSpeedMultiplier(world));
        }
        if (definition.armor() != null) {
            setAttribute(entity, Attribute.ARMOR, definition.armor() * config.getArmorMultiplier(world));
        }
        if (definition.followRange() != null) {
            setAttribute(entity, Attribute.FOLLOW_RANGE, definition.followRange() * config.getFollowRangeMultiplier(world));
        }
        if (definition.knockbackResistance() != null) {
            setAttribute(entity, Attribute.KNOCKBACK_RESISTANCE, definition.knockbackResistance());
        }
        if (definition.scale() != null) {
            setAttribute(entity, Attribute.SCALE, definition.scale());
        }
    }

    private AttributeInstance setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(Math.max(0.0, value));
        }
        return instance;
    }

    private void applyEquipment(LivingEntity entity, Map<EquipmentSlot, ItemStack> equipment) {
        EntityEquipment entityEquipment = entity.getEquipment();
        if (entityEquipment == null) {
            return;
        }
        for (Map.Entry<EquipmentSlot, ItemStack> entry : equipment.entrySet()) {
            entityEquipment.setItem(entry.getKey(), entry.getValue(), true);
            entityEquipment.setDropChance(entry.getKey(), 0.0f);
        }
    }

    private int randomBetween(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }
}
