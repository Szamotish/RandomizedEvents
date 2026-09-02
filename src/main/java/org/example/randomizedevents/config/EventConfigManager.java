package org.example.randomizedevents.config;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

public final class EventConfigManager {

    private final JavaPlugin plugin;
    private final Map<String, EventDefinition> events = new HashMap<>();
    private final Map<String, MobDefinition> mobClasses = new HashMap<>();
    private final Map<String, GearProfile> gearProfiles = new HashMap<>();
    private final Map<String, AnchorEventDefinition> anchorEvents = new HashMap<>();

    private boolean enabled;
    private boolean announceEvents;
    private boolean requireTargetInSurvival;
    private boolean cleanupOnDisable;
    private boolean cleanupOnReload;
    private boolean clearVanillaDrops;
    private int minOnlinePlayers;
    private int minIntervalSeconds;
    private int maxIntervalSeconds;
    private int despawnAfterSeconds;
    private int safeRadiusFromWorldSpawn;
    private boolean gearScalingEnabled;
    private boolean worldScalingEnabled;
    private boolean requireLoadedChunkForSpawn;
    private int daysPerGearStage;
    private int maxGearStage;
    private int gearStageVariance;
    private Integer gearStageOverride;
    private int daysPerWorldStage;
    private int maxWorldStage;
    private Integer worldStageOverride;
    private double healthBonusPerWorldStage;
    private double maxHealthBonus;
    private double attackDamageBonusPerWorldStage;
    private double maxAttackDamageBonus;
    private double armorBonusPerWorldStage;
    private double maxArmorBonus;
    private double movementSpeedBonusPerWorldStage;
    private double maxMovementSpeedBonus;
    private double followRangeBonusPerWorldStage;
    private double maxFollowRangeBonus;
    private double budgetBonusPerWorldStage;
    private double maxBudgetBonus;
    private double countExtraChancePerWorldStage;
    private double maxCountExtraChance;
    private int maxExtraMobsPerClass;
    private double lootChanceBonusPerWorldStage;
    private double maxLootChanceBonus;
    private double lootExtraAmountChancePerWorldStage;
    private double maxLootExtraAmountChance;
    private int maxLootExtraAmount;
    private int maxEventMobsInRadius;
    private int eventMobDensityRadius;
    private int minDistanceFromBedSpawns;
    private boolean despawnWhenNoPlayersNearby;
    private int noPlayerDespawnRadius;
    private int noPlayerDespawnDelaySeconds;
    private boolean despawnTargetBoundWhenTargetUnavailable;
    private int targetUnavailableDelaySeconds;
    private int targetUnavailableCombatGraceSeconds;
    private Set<String> targetUnavailableBehaviors;
    private Set<String> targetUnavailableEvents;
    private Set<Biome> disabledBiomes;
    private Set<String> disabledWorlds;
    private String prefix;

    public EventConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        this.enabled = plugin.getConfig().getBoolean("settings.enabled", true);
        this.announceEvents = plugin.getConfig().getBoolean("settings.announce-events", true);
        this.minOnlinePlayers = Math.max(1, plugin.getConfig().getInt("settings.min-online-players", 1));
        this.requireTargetInSurvival = plugin.getConfig().getBoolean("settings.require-target-in-survival", true);
        this.cleanupOnDisable = plugin.getConfig().getBoolean("settings.cleanup-event-mobs-on-disable", true);
        this.cleanupOnReload = plugin.getConfig().getBoolean("settings.cleanup-event-mobs-on-reload", false);
        this.clearVanillaDrops = plugin.getConfig().getBoolean("settings.clear-vanilla-drops-from-event-mobs", true);
        this.despawnAfterSeconds = Math.max(0, plugin.getConfig().getInt("settings.despawn-after-seconds", 1800));
        this.safeRadiusFromWorldSpawn = Math.max(0, plugin.getConfig().getInt("settings.safe-radius-from-world-spawn", 64));
        this.gearScalingEnabled = plugin.getConfig().getBoolean("settings.gear-scaling.enabled", true);
        this.worldScalingEnabled = plugin.getConfig().getBoolean("settings.world-scaling.enabled", true);
        this.requireLoadedChunkForSpawn = plugin.getConfig().getBoolean("settings.spawn-safety.require-loaded-chunk", true);
        this.maxEventMobsInRadius = Math.max(0, plugin.getConfig().getInt("settings.spawn-safety.max-event-mobs-in-radius", 24));
        this.eventMobDensityRadius = Math.max(1, plugin.getConfig().getInt("settings.spawn-safety.event-mob-density-radius", 48));
        this.minDistanceFromBedSpawns = Math.max(0, plugin.getConfig().getInt("settings.spawn-safety.min-distance-from-online-bed-spawns", 32));
        this.despawnWhenNoPlayersNearby = plugin.getConfig().getBoolean("settings.despawn.when-no-players-nearby.enabled", true);
        this.noPlayerDespawnRadius = Math.max(1, plugin.getConfig().getInt("settings.despawn.when-no-players-nearby.radius", 96));
        this.noPlayerDespawnDelaySeconds = Math.max(1, plugin.getConfig().getInt("settings.despawn.when-no-players-nearby.delay-seconds", 180));
        this.despawnTargetBoundWhenTargetUnavailable = plugin.getConfig().getBoolean("settings.despawn.target-bound-when-target-unavailable.enabled", true);
        this.targetUnavailableDelaySeconds = Math.max(1, plugin.getConfig().getInt("settings.despawn.target-bound-when-target-unavailable.delay-seconds", 45));
        this.targetUnavailableCombatGraceSeconds = Math.max(0, plugin.getConfig().getInt("settings.despawn.target-bound-when-target-unavailable.combat-grace-seconds", 5));
        this.targetUnavailableBehaviors = Set.copyOf(plugin.getConfig().getStringList("settings.despawn.target-bound-when-target-unavailable.behaviors").stream()
                .map(this::normalizeId)
                .filter(Objects::nonNull)
                .toList());
        this.targetUnavailableEvents = Set.copyOf(plugin.getConfig().getStringList("settings.despawn.target-bound-when-target-unavailable.events").stream()
                .map(this::normalizeId)
                .filter(Objects::nonNull)
                .toList());
        this.daysPerGearStage = Math.max(1, plugin.getConfig().getInt("settings.gear-scaling.days-per-stage", 4));
        this.maxGearStage = Math.max(0, plugin.getConfig().getInt("settings.gear-scaling.max-stage", 24));
        this.gearStageVariance = Math.max(0, plugin.getConfig().getInt("settings.gear-scaling.random-stage-variance", 1));
        this.gearStageOverride = getStageOverride("settings.gear-scaling.stage-override");
        this.daysPerWorldStage = Math.max(1, plugin.getConfig().getInt("settings.world-scaling.days-per-stage", this.daysPerGearStage));
        this.maxWorldStage = Math.max(0, plugin.getConfig().getInt("settings.world-scaling.max-stage", this.maxGearStage));
        this.worldStageOverride = getStageOverride("settings.world-scaling.stage-override");
        this.healthBonusPerWorldStage = Math.max(0.0, plugin.getConfig().getDouble("settings.world-scaling.stats.health-bonus-per-stage", 0.0125));
        this.maxHealthBonus = Math.max(0.0, plugin.getConfig().getDouble("settings.world-scaling.stats.max-health-bonus", 0.30));
        this.attackDamageBonusPerWorldStage = Math.max(0.0, plugin.getConfig().getDouble("settings.world-scaling.stats.attack-damage-bonus-per-stage", 0.01));
        this.maxAttackDamageBonus = Math.max(0.0, plugin.getConfig().getDouble("settings.world-scaling.stats.max-attack-damage-bonus", 0.24));
        this.armorBonusPerWorldStage = Math.max(0.0, plugin.getConfig().getDouble("settings.world-scaling.stats.armor-bonus-per-stage", 0.008));
        this.maxArmorBonus = Math.max(0.0, plugin.getConfig().getDouble("settings.world-scaling.stats.max-armor-bonus", 0.18));
        this.movementSpeedBonusPerWorldStage = Math.max(0.0, plugin.getConfig().getDouble("settings.world-scaling.stats.movement-speed-bonus-per-stage", 0.002));
        this.maxMovementSpeedBonus = Math.max(0.0, plugin.getConfig().getDouble("settings.world-scaling.stats.max-movement-speed-bonus", 0.05));
        this.followRangeBonusPerWorldStage = Math.max(0.0, plugin.getConfig().getDouble("settings.world-scaling.stats.follow-range-bonus-per-stage", 0.006));
        this.maxFollowRangeBonus = Math.max(0.0, plugin.getConfig().getDouble("settings.world-scaling.stats.max-follow-range-bonus", 0.15));
        this.budgetBonusPerWorldStage = Math.max(0.0, plugin.getConfig().getDouble("settings.world-scaling.event-budget.bonus-per-stage", 0.015));
        this.maxBudgetBonus = Math.max(0.0, plugin.getConfig().getDouble("settings.world-scaling.event-budget.max-bonus", 0.35));
        this.countExtraChancePerWorldStage = Math.max(0.0, plugin.getConfig().getDouble("settings.world-scaling.mob-count.extra-chance-per-stage", 0.006));
        this.maxCountExtraChance = clampChance(plugin.getConfig().getDouble("settings.world-scaling.mob-count.max-extra-chance", 0.15));
        this.maxExtraMobsPerClass = Math.max(0, plugin.getConfig().getInt("settings.world-scaling.mob-count.max-extra-mobs-per-class", 1));
        this.lootChanceBonusPerWorldStage = Math.max(0.0, plugin.getConfig().getDouble("settings.world-scaling.loot.chance-bonus-per-stage", 0.003));
        this.maxLootChanceBonus = Math.max(0.0, plugin.getConfig().getDouble("settings.world-scaling.loot.max-chance-bonus", 0.08));
        this.lootExtraAmountChancePerWorldStage = Math.max(0.0, plugin.getConfig().getDouble("settings.world-scaling.loot.extra-amount-chance-per-stage", 0.004));
        this.maxLootExtraAmountChance = clampChance(plugin.getConfig().getDouble("settings.world-scaling.loot.max-extra-amount-chance", 0.10));
        this.maxLootExtraAmount = Math.max(0, plugin.getConfig().getInt("settings.world-scaling.loot.max-extra-amount", 1));
        this.minIntervalSeconds = Math.max(1, plugin.getConfig().getInt("settings.interval-seconds.min", 300));
        this.maxIntervalSeconds = Math.max(minIntervalSeconds, plugin.getConfig().getInt("settings.interval-seconds.max", 900));
        this.disabledWorlds = Set.copyOf(plugin.getConfig().getStringList("settings.disabled-worlds").stream()
                .map(world -> world.toLowerCase(Locale.ROOT))
                .toList());
        this.disabledBiomes = Set.copyOf(plugin.getConfig().getStringList("settings.spawn-safety.disabled-biomes").stream()
                .map(this::parseBiome)
                .filter(Objects::nonNull)
                .toList());
        this.prefix = color(plugin.getConfig().getString("messages.prefix", "&8[&cEvents&8]&r "));

        gearProfiles.clear();
        loadGearProfiles();

        mobClasses.clear();
        loadMobClasses();

        events.clear();
        loadEvents();

        anchorEvents.clear();
        loadAnchorEvents();
    }

    private void loadGearProfiles() {
        ConfigurationSection profilesSection = plugin.getConfig().getConfigurationSection("gear-profiles");
        if (profilesSection == null) {
            return;
        }

        for (String id : profilesSection.getKeys(false)) {
            ConfigurationSection section = profilesSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            GearProfile profile = parseGearProfile(id, section);
            if (!profile.slots().isEmpty()) {
                gearProfiles.put(profile.id(), profile);
            }
        }
    }

    private GearProfile parseGearProfile(String id, ConfigurationSection section) {
        Map<EquipmentSlot, GearSlotDefinition> slots = new EnumMap<>(EquipmentSlot.class);
        for (String key : section.getKeys(false)) {
            EquipmentSlot slot = parseSlot(key);
            ConfigurationSection slotSection = section.getConfigurationSection(key);
            if (slot == null || slotSection == null) {
                continue;
            }

            List<GearStep> steps = new ArrayList<>();
            for (Map<?, ?> rawStep : slotSection.getMapList("steps")) {
                ItemStack item = parseItem(rawStep);
                if (item == null) {
                    continue;
                }
                steps.add(new GearStep(
                        Math.max(0, getInt(rawStep, "stage", 0)),
                        Math.max(1, getInt(rawStep, "weight", 1)),
                        item
                ));
            }
            steps.sort((left, right) -> Integer.compare(left.stage(), right.stage()));
            if (!steps.isEmpty()) {
                slots.put(slot, new GearSlotDefinition(clampChance(slotSection.getDouble("chance", 1.0)), List.copyOf(steps)));
            }
        }
        return new GearProfile(normalizeId(id), Map.copyOf(slots));
    }

    private void loadMobClasses() {
        ConfigurationSection classesSection = plugin.getConfig().getConfigurationSection("mob-classes");
        if (classesSection == null) {
            plugin.getLogger().warning("No mob-classes section found in config.yml.");
            return;
        }

        for (String id : classesSection.getKeys(false)) {
            ConfigurationSection section = classesSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            MobDefinition definition = parseMobClass(id, section);
            if (definition != null) {
                mobClasses.put(definition.id(), definition);
            }
        }
    }

    private void loadEvents() {
        ConfigurationSection eventsSection = plugin.getConfig().getConfigurationSection("events");
        if (eventsSection == null) {
            plugin.getLogger().warning("No events section found in config.yml.");
            return;
        }

        for (String id : eventsSection.getKeys(false)) {
            ConfigurationSection section = eventsSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            EventDefinition definition = parseEvent(id, section);
            if (!definition.required().isEmpty() || !definition.pool().isEmpty()) {
                events.put(definition.id(), definition);
            } else {
                plugin.getLogger().warning("Event '" + id + "' has no pool entries and was skipped.");
            }
        }
    }

    private void loadAnchorEvents() {
        ConfigurationSection anchorsSection = plugin.getConfig().getConfigurationSection("anchor-events");
        if (anchorsSection == null) {
            return;
        }

        for (String id : anchorsSection.getKeys(false)) {
            ConfigurationSection section = anchorsSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String eventId = normalizeId(id);
            if (!events.containsKey(eventId)) {
                plugin.getLogger().warning("Anchor event '" + id + "' does not match any configured event.");
                continue;
            }
            Material bannerMaterial = parseEnum(Material.class, section.getString("banner-material", "RED_BANNER"));
            if (bannerMaterial == null || !bannerMaterial.name().endsWith("_BANNER")) {
                plugin.getLogger().warning("Invalid banner material for anchor event '" + id + "'.");
                continue;
            }
            List<String> subEvents = section.getStringList("sub-events").stream()
                    .map(this::normalizeId)
                    .filter(Objects::nonNull)
                    .filter(events::containsKey)
                    .toList();
            if (subEvents.isEmpty()) {
                plugin.getLogger().warning("Anchor event '" + id + "' has no valid sub-events.");
                continue;
            }
            anchorEvents.put(eventId, new AnchorEventDefinition(
                    eventId,
                    bannerMaterial,
                    Math.max(1, section.getInt("sub-event-interval-seconds", 1800)),
                    Math.max(1, section.getInt("sub-event-radius", 1000)),
                    Math.max(1, section.getInt("no-target-timeout-seconds", 1800)),
                    Math.max(1, section.getInt("banner-destroyed-despawn-seconds", 1800)),
                    Math.max(1, section.getInt("guard-leash-radius", 28)),
                    Math.max(1, section.getInt("guard-awake-radius", 48)),
                    parseSmokeMarker(section.getConfigurationSection("smoke-marker"), id),
                    List.copyOf(subEvents)
            ));
        }
    }

    private AnchorSmokeMarkerDefinition parseSmokeMarker(ConfigurationSection section, String anchorId) {
        if (section == null || !section.getBoolean("enabled", false)) {
            return new AnchorSmokeMarkerDefinition(false, Particle.CAMPFIRE_SIGNAL_SMOKE, null, 0, 0, 0, 0, 0, 0.0);
        }

        Particle particle = parseEnum(Particle.class, section.getString("particle", "CAMPFIRE_SIGNAL_SMOKE"));
        if (particle == null) {
            plugin.getLogger().warning("Invalid smoke marker particle for anchor event '" + anchorId + "'.");
            particle = Particle.CAMPFIRE_SIGNAL_SMOKE;
        }
        Material sourceBlock = parseEnum(Material.class, section.getString("source-block", null));
        if (sourceBlock != null && sourceBlock != Material.CAMPFIRE && sourceBlock != Material.SOUL_CAMPFIRE) {
            plugin.getLogger().warning("Invalid smoke marker source block for anchor event '" + anchorId + "'.");
            sourceBlock = null;
        }

        return new AnchorSmokeMarkerDefinition(
                true,
                particle,
                sourceBlock,
                Math.max(0, section.getInt("source-radius", 3)),
                Math.max(1, section.getInt("height", 22)),
                Math.max(1, section.getInt("interval-seconds", 3)),
                Math.max(1, section.getInt("points", 7)),
                Math.max(1, section.getInt("count", 1)),
                Math.max(0.0, section.getDouble("spread", 0.35))
        );
    }

    private EventDefinition parseEvent(String id, ConfigurationSection section) {
        String eventId = normalizeId(id);
        String displayName = section.getString("display-name", id);
        boolean eventEnabled = section.getBoolean("enabled", true);
        double weight = Math.max(0.0, section.getDouble("weight", 1.0));
        double weightChangePerWorldStage = section.getDouble("scaling.weight-change-per-stage", 0.0);
        double maxWeightChange = Math.max(0.0, section.getDouble("scaling.max-weight-change", 0.0));
        int minDistance = Math.max(1, section.getInt("min-distance", 20));
        int maxDistance = Math.max(minDistance, section.getInt("max-distance", 50));
        int spreadRadius = Math.max(0, section.getInt("spread-radius", 5));
        int announcementRadius = Math.max(0, section.getInt("announcement-radius", 160));
        String announcementMessage = section.getString("announcement-message");
        String targetMessage = section.getString("target-message");
        boolean lightningMarker = section.getBoolean("lightning-marker", false);
        int budgetMin = Math.max(0, section.getInt("budget.min", 3));
        int budgetMax = Math.max(budgetMin, section.getInt("budget.max", budgetMin));
        double addChanceStart = clampChance(section.getDouble("selection.add-chance-start", 0.85));
        double addChanceDecay = clampChance(section.getDouble("selection.add-chance-decay", 0.55));
        int maxPicks = Math.max(0, section.getInt("selection.max-picks", 6));
        double customLootChance = clampChance(section.getDouble("custom-loot-chance", 0.0));

        List<EventPoolEntry> required = parsePool(section.getMapList("required"), eventId, true);
        List<EventPoolEntry> pool = parsePool(section.getMapList("pool"), eventId, false);
        List<LootDefinition> loot = parseLootList(section.getMapList("loot"), eventId);

        return new EventDefinition(eventId, displayName, eventEnabled, weight, weightChangePerWorldStage,
                maxWeightChange, minDistance, maxDistance,
                spreadRadius, announcementRadius, announcementMessage, targetMessage, lightningMarker, budgetMin, budgetMax, addChanceStart,
                addChanceDecay, maxPicks, customLootChance, List.copyOf(required), List.copyOf(pool),
                List.copyOf(loot));
    }

    private List<EventPoolEntry> parsePool(List<Map<?, ?>> rawPool, String eventId, boolean required) {
        List<EventPoolEntry> entries = new ArrayList<>();
        for (Map<?, ?> rawEntry : rawPool) {
            String classId = normalizeId(getString(rawEntry, "class", null));
            if (classId == null || !mobClasses.containsKey(classId)) {
                plugin.getLogger().warning("Unknown mob class '" + classId + "' in event '" + eventId + "'.");
                continue;
            }
            int weight = required ? 1 : Math.max(0, getInt(rawEntry, "weight", 1));
            if (!required && weight <= 0) {
                continue;
            }
            int minPicks = Math.max(1, getInt(rawEntry, "picks-min", 1));
            int maxPicks = Math.max(minPicks, getInt(rawEntry, "picks-max", minPicks));
            double costMultiplier = Math.max(0.1, getDouble(rawEntry, "cost-multiplier", 1.0));
            entries.add(new EventPoolEntry(classId, weight, minPicks, maxPicks, costMultiplier));
        }
        return entries;
    }

    private MobDefinition parseMobClass(String id, ConfigurationSection section) {
        String classId = normalizeId(id);
        EntityType type = parseEnum(EntityType.class, section.getString("type"));
        if (type == null || !type.isSpawnable()) {
            plugin.getLogger().warning("Invalid mob type '" + section.getString("type") + "' in mob class '" + id + "'.");
            return null;
        }

        EntityType mountType = parseEnum(EntityType.class, section.getString("mount.type"));
        if (mountType != null && !mountType.isSpawnable()) {
            mountType = null;
        }

        List<String> behaviors = section.getStringList("behaviors").stream()
                .map(this::normalizeId)
                .filter(Objects::nonNull)
                .toList();

        return new MobDefinition(
                classId,
                type,
                mountType,
                Math.max(1, section.getInt("count-min", 1)),
                Math.max(section.getInt("count-min", 1), section.getInt("count-max", section.getInt("count-min", 1))),
                Math.max(1, section.getInt("cost", 1)),
                normalizeId(section.getString("gear-profile")),
                section.getString("name"),
                section.getString("announcement-message"),
                getSectionDouble(section, "health"),
                getSectionDouble(section, "attack-damage"),
                getSectionDouble(section, "movement-speed"),
                getSectionDouble(section, "armor"),
                getSectionDouble(section, "follow-range"),
                getSectionDouble(section, "knockback-resistance"),
                getSectionDouble(section, "scale"),
                section.getBoolean("baby", false),
                section.getBoolean("persistent", false),
                section.getBoolean("glowing", false),
                section.getBoolean("immune-to-zombification", false),
                section.getString("rabbit-type"),
                getSectionInteger(section, "fire-ticks"),
                getSectionInteger(section, "creeper.fuse-ticks"),
                getSectionInteger(section, "creeper.explosion-radius"),
                section.isBoolean("creeper.powered") ? section.getBoolean("creeper.powered") : null,
                getSectionDouble(section, "medic.heal-amount"),
                getSectionDouble(section, "medic.radius"),
                getSectionInteger(section, "medic.interval-seconds"),
                section.getStringList("summon.classes").stream().map(this::normalizeId).filter(Objects::nonNull).toList(),
                getSectionInteger(section, "summon.interval-seconds"),
                getSectionInteger(section, "summon.count-min"),
                getSectionInteger(section, "summon.count-max"),
                getSectionDouble(section, "summon.radius"),
                getSectionInteger(section, "poison-on-hit.seconds"),
                getSectionInteger(section, "poison-on-hit.amplifier"),
                getSectionInteger(section, "invisibility-on-target-seconds"),
                section.getString("self-effect-on-target.effect"),
                getSectionInteger(section, "self-effect-on-target.duration-seconds"),
                getSectionInteger(section, "self-effect-on-target.amplifier"),
                section.getString("debuff.effect"),
                getSectionInteger(section, "debuff.duration-seconds"),
                getSectionInteger(section, "debuff.amplifier"),
                getSectionDouble(section, "debuff.radius"),
                getSectionInteger(section, "debuff.interval-seconds"),
                getSectionDouble(section, "trader.ambush-chance"),
                parseTrades(section.getMapList("trades"), classId),
                normalizeId(section.getString("boss-token.id")),
                section.getString("boss-token.name"),
                parseLootList(section.getMapList("loot"), classId),
                behaviors,
                parseEquipment(section.getConfigurationSection("equipment")),
                parsePotionEffects(section.getMapList("potion-effects"), classId)
        );
    }

    private List<LootDefinition> parseLootList(List<Map<?, ?>> rawLootList, String ownerId) {
        List<LootDefinition> loot = new ArrayList<>();
        for (Map<?, ?> rawLoot : rawLootList) {
            LootDefinition lootDefinition = parseLoot(rawLoot, ownerId);
            if (lootDefinition != null) {
                loot.add(lootDefinition);
            }
        }
        return loot;
    }

    private LootDefinition parseLoot(Map<?, ?> rawLoot, String ownerId) {
        ItemStack item = parseItem(rawLoot);
        if (item == null) {
            plugin.getLogger().warning("Invalid loot item in '" + ownerId + "'.");
            return null;
        }
        int minAmount = Math.max(1, getInt(rawLoot, "amount-min", item.getAmount()));
        int maxAmount = Math.max(minAmount, getInt(rawLoot, "amount-max", minAmount));
        double chance = clampChance(getDouble(rawLoot, "chance", 1.0));
        boolean worldScaling = getBoolean(rawLoot, "world-scaling", true);
        item.setAmount(1);
        return new LootDefinition(item, minAmount, maxAmount, chance, worldScaling);
    }

    private List<TradeDefinition> parseTrades(List<Map<?, ?>> rawTrades, String classId) {
        List<TradeDefinition> trades = new ArrayList<>();
        for (Map<?, ?> rawTrade : rawTrades) {
            Object resultRaw = rawTrade.get("result");
            ItemStack result = resultRaw instanceof Map<?, ?> map ? parseItem(map) : parseItem(rawTrade);
            if (result == null) {
                plugin.getLogger().warning("Invalid trade result in mob class '" + classId + "'.");
                continue;
            }

            List<ItemStack> ingredients = new ArrayList<>();
            Object rawIngredients = rawTrade.get("ingredients");
            if (rawIngredients instanceof List<?> list) {
                for (Object ingredientRaw : list) {
                    ItemStack ingredient = parseItem(ingredientRaw);
                    if (ingredient != null) {
                        ingredients.add(ingredient);
                    }
                }
            }
            if (ingredients.isEmpty()) {
                plugin.getLogger().warning("Trade in mob class '" + classId + "' has no valid ingredients.");
                continue;
            }
            trades.add(new TradeDefinition(result, List.copyOf(ingredients.subList(0, Math.min(2, ingredients.size()))),
                    Math.max(1, getInt(rawTrade, "max-uses", 8))));
        }
        return trades;
    }

    private Map<EquipmentSlot, ItemStack> parseEquipment(ConfigurationSection section) {
        if (section == null) {
            return Collections.emptyMap();
        }

        Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        for (String key : section.getKeys(false)) {
            EquipmentSlot slot = parseSlot(key);
            if (slot == null) {
                continue;
            }
            Object raw = section.get(key);
            ItemStack item = parseItem(raw);
            if (item != null) {
                equipment.put(slot, item);
            }
        }
        return equipment;
    }

    private ItemStack parseItem(Object rawItem) {
        if (rawItem instanceof String materialName) {
            Material material = parseEnum(Material.class, materialName);
            return material == null || material.isAir() ? null : new ItemStack(material);
        }
        if (!(rawItem instanceof Map<?, ?> itemMap)) {
            return null;
        }
        ItemStack bossToken = parseBossTokenItem(itemMap);
        if (bossToken != null) {
            return bossToken;
        }
        Material material = parseEnum(Material.class, getString(itemMap, "material", null));
        if (material == null || material.isAir()) {
            return null;
        }
        int amount = Math.max(1, getInt(itemMap, "amount", 1));
        ItemStack item = new ItemStack(material, amount);
        applyItemMeta(item, itemMap);
        return item;
    }

    private void applyItemMeta(ItemStack item, Map<?, ?> itemMap) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        String name = getString(itemMap, "name", null);
        if (name != null && !name.isBlank()) {
            meta.setDisplayName(color(name));
        }

        Object rawLore = itemMap.get("lore");
        if (rawLore instanceof List<?> loreValues) {
            List<String> lore = loreValues.stream()
                    .filter(Objects::nonNull)
                    .map(value -> color(String.valueOf(value)))
                    .toList();
            meta.setLore(lore);
        }

        meta.setUnbreakable(getBoolean(itemMap, "unbreakable", false));
        if (getBoolean(itemMap, "hide-flags", false)) {
            meta.addItemFlags(ItemFlag.values());
        }

        applyPotionEffects(meta, itemMap.get("potion-effects"));
        applyEnchantments(item, meta, itemMap.get("enchantments"));
        item.setItemMeta(meta);
    }

    private ItemStack parseBossTokenItem(Map<?, ?> itemMap) {
        String tokenId = normalizeId(getString(itemMap, "boss-token-id", null));
        if (tokenId == null) {
            tokenId = normalizeId(getString(itemMap, "boss-token", null));
        }
        if (tokenId == null) {
            return null;
        }

        int amount = Math.max(1, getInt(itemMap, "amount", 1));
        ItemStack token = new ItemStack(Material.ECHO_SHARD, amount);
        ItemMeta meta = token.getItemMeta();
        if (meta == null) {
            return token;
        }

        String name = getString(itemMap, "name", null);
        meta.setDisplayName(color(name == null || name.isBlank() ? "&5Boss Token" : name));
        meta.setLore(List.of("A sealed proof of an event boss kill.", "Keep it, trade it, or submit it."));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "boss_token"), PersistentDataType.INTEGER, 1);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "boss_token_id"), PersistentDataType.STRING, tokenId);
        token.setItemMeta(meta);
        return token;
    }

    private void applyEnchantments(ItemStack item, ItemMeta meta, Object rawEnchantments) {
        if (!(rawEnchantments instanceof Map<?, ?> enchantments)) {
            return;
        }
        for (Map.Entry<?, ?> entry : enchantments.entrySet()) {
            Enchantment enchantment = Enchantment.getByName(String.valueOf(entry.getKey()).toUpperCase(Locale.ROOT));
            if (enchantment == null) {
                continue;
            }
            int level = parseIntValue(entry.getValue(), 1);
            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(enchantment, Math.max(1, level), true);
            } else {
                meta.addEnchant(enchantment, Math.max(1, level), true);
            }
        }
    }

    private void applyPotionEffects(ItemMeta meta, Object rawEffects) {
        if (!(meta instanceof PotionMeta potionMeta) || !(rawEffects instanceof List<?> effects)) {
            return;
        }
        for (Object rawEffect : effects) {
            if (!(rawEffect instanceof Map<?, ?> effectMap)) {
                continue;
            }
            String rawType = getString(effectMap, "type", null);
            PotionEffectType type = PotionEffectType.getByName(String.valueOf(rawType).toUpperCase(Locale.ROOT));
            if (type == null) {
                continue;
            }
            int amplifier = Math.max(0, getInt(effectMap, "amplifier", 0));
            int durationSeconds = Math.max(1, getInt(effectMap, "duration-seconds", 60));
            potionMeta.addCustomEffect(new PotionEffect(type, durationSeconds * 20, amplifier), true);
        }
    }

    private List<PotionEffect> parsePotionEffects(List<Map<?, ?>> effects, String ownerId) {
        List<PotionEffect> parsed = new ArrayList<>();
        for (Map<?, ?> effectMap : effects) {
            String rawType = getString(effectMap, "type", null);
            PotionEffectType type = PotionEffectType.getByName(String.valueOf(rawType).toUpperCase(Locale.ROOT));
            if (type == null) {
                plugin.getLogger().warning("Invalid potion effect '" + rawType + "' in '" + ownerId + "'.");
                continue;
            }
            int amplifier = Math.max(0, getInt(effectMap, "amplifier", 0));
            int durationSeconds = Math.max(1, getInt(effectMap, "duration-seconds", 60));
            parsed.add(new PotionEffect(type, durationSeconds * 20, amplifier, true, true, true));
        }
        return parsed;
    }

    private EquipmentSlot parseSlot(String rawSlot) {
        String normalized = rawSlot.toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "MAIN_HAND", "HAND" -> EquipmentSlot.HAND;
            case "OFF_HAND" -> EquipmentSlot.OFF_HAND;
            case "HELMET", "HEAD" -> EquipmentSlot.HEAD;
            case "CHESTPLATE", "CHEST" -> EquipmentSlot.CHEST;
            case "LEGGINGS", "LEGS" -> EquipmentSlot.LEGS;
            case "BOOTS", "FEET" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    private <T extends Enum<T>> T parseEnum(Class<T> type, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, rawValue.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.FINE, "Invalid enum value " + rawValue, ex);
            return null;
        }
    }

    private Biome parseBiome(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String key = rawValue.toLowerCase(Locale.ROOT).replace('_', '-');
        return Registry.BIOME.get(NamespacedKey.minecraft(key));
    }

    private String normalizeId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private String getString(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private int getInt(Map<?, ?> map, String key, int fallback) {
        return parseIntValue(map.get(key), fallback);
    }

    private int parseIntValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private Integer getSectionInteger(ConfigurationSection section, String path) {
        return section.isSet(path) ? section.getInt(path) : null;
    }

    private Integer getStageOverride(String path) {
        if (!plugin.getConfig().isSet(path)) {
            return null;
        }
        int value = plugin.getConfig().getInt(path, -1);
        return value >= 0 ? value : null;
    }

    private Double getSectionDouble(ConfigurationSection section, String path) {
        return section.isSet(path) ? section.getDouble(path) : null;
    }

    private Double getDouble(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private double getDouble(Map<?, ?> map, String key, double fallback) {
        Double value = getDouble(map, key);
        return value == null ? fallback : value;
    }

    private boolean getBoolean(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private double clampChance(double chance) {
        return Math.max(0.0, Math.min(1.0, chance));
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    public String message(String path) {
        return prefix + color(plugin.getConfig().getString("messages." + path, ""));
    }

    public String inlineMessage(String text) {
        return prefix + color(text);
    }

    public Map<String, EventDefinition> getEvents() {
        return Collections.unmodifiableMap(events);
    }

    public EventDefinition getEvent(String id) {
        return events.get(normalizeId(id));
    }

    public List<EventDefinition> getEnabledEvents() {
        return events.values().stream()
                .filter(event -> event.enabled() && event.weight() > 0)
                .toList();
    }

    public Map<String, MobDefinition> getMobClasses() {
        return Collections.unmodifiableMap(mobClasses);
    }

    public MobDefinition getMobClass(String id) {
        return mobClasses.get(normalizeId(id));
    }

    public Map<String, GearProfile> getGearProfiles() {
        return Collections.unmodifiableMap(gearProfiles);
    }

    public GearProfile getGearProfile(String id) {
        return gearProfiles.get(normalizeId(id));
    }

    public AnchorEventDefinition getAnchorEvent(String eventId) {
        return anchorEvents.get(normalizeId(eventId));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setRuntimeEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAnnounceEvents() {
        return announceEvents;
    }

    public boolean isRequireTargetInSurvival() {
        return requireTargetInSurvival;
    }

    public boolean isCleanupOnDisable() {
        return cleanupOnDisable;
    }

    public boolean isCleanupOnReload() {
        return cleanupOnReload;
    }

    public boolean isClearVanillaDrops() {
        return clearVanillaDrops;
    }

    public int getMinOnlinePlayers() {
        return minOnlinePlayers;
    }

    public int getMinIntervalSeconds() {
        return minIntervalSeconds;
    }

    public int getMaxIntervalSeconds() {
        return maxIntervalSeconds;
    }

    public int getDespawnAfterSeconds() {
        return despawnAfterSeconds;
    }

    public int getSafeRadiusFromWorldSpawn() {
        return safeRadiusFromWorldSpawn;
    }

    public boolean isGearScalingEnabled() {
        return gearScalingEnabled;
    }

    public int getGearScalingStage(World world) {
        if (!gearScalingEnabled || world == null) {
            return 0;
        }
        if (gearStageOverride != null) {
            return Math.max(0, Math.min(maxGearStage, gearStageOverride));
        }
        long days = Math.max(0L, world.getFullTime() / 24000L);
        int stage = (int) Math.min(Integer.MAX_VALUE, days / daysPerGearStage);
        return Math.max(0, Math.min(maxGearStage, stage));
    }

    public boolean isGearStageOverridden() {
        return gearStageOverride != null;
    }

    public Integer getGearStageOverride() {
        return gearStageOverride;
    }

    public void setGearStageOverride(Integer stage) {
        this.gearStageOverride = stage == null ? null : Math.max(0, Math.min(maxGearStage, stage));
        plugin.getConfig().set("settings.gear-scaling.stage-override", this.gearStageOverride == null ? -1 : this.gearStageOverride);
        plugin.saveConfig();
    }

    public boolean isWorldScalingEnabled() {
        return worldScalingEnabled;
    }

    public int getWorldScalingStage(World world) {
        if (!worldScalingEnabled || world == null) {
            return 0;
        }
        if (worldStageOverride != null) {
            return Math.max(0, Math.min(maxWorldStage, worldStageOverride));
        }
        long days = Math.max(0L, world.getFullTime() / 24000L);
        int stage = (int) Math.min(Integer.MAX_VALUE, days / daysPerWorldStage);
        return Math.max(0, Math.min(maxWorldStage, stage));
    }

    public boolean isWorldStageOverridden() {
        return worldStageOverride != null;
    }

    public Integer getWorldStageOverride() {
        return worldStageOverride;
    }

    public void setWorldStageOverride(Integer stage) {
        this.worldStageOverride = stage == null ? null : Math.max(0, Math.min(maxWorldStage, stage));
        plugin.getConfig().set("settings.world-scaling.stage-override", this.worldStageOverride == null ? -1 : this.worldStageOverride);
        plugin.saveConfig();
    }

    public double getHealthMultiplier(World world) {
        return 1.0 + cappedBonus(getWorldScalingStage(world), healthBonusPerWorldStage, maxHealthBonus);
    }

    public double getAttackDamageMultiplier(World world) {
        return 1.0 + cappedBonus(getWorldScalingStage(world), attackDamageBonusPerWorldStage, maxAttackDamageBonus);
    }

    public double getArmorMultiplier(World world) {
        return 1.0 + cappedBonus(getWorldScalingStage(world), armorBonusPerWorldStage, maxArmorBonus);
    }

    public double getMovementSpeedMultiplier(World world) {
        return 1.0 + cappedBonus(getWorldScalingStage(world), movementSpeedBonusPerWorldStage, maxMovementSpeedBonus);
    }

    public double getFollowRangeMultiplier(World world) {
        return 1.0 + cappedBonus(getWorldScalingStage(world), followRangeBonusPerWorldStage, maxFollowRangeBonus);
    }

    public double getEventBudgetMultiplier(World world) {
        return 1.0 + cappedBonus(getWorldScalingStage(world), budgetBonusPerWorldStage, maxBudgetBonus);
    }

    public double getMobCountExtraChance(World world) {
        return Math.min(maxCountExtraChance, getWorldScalingStage(world) * countExtraChancePerWorldStage);
    }

    public int getMaxExtraMobsPerClass() {
        return maxExtraMobsPerClass;
    }

    public double getLootChanceMultiplier(World world) {
        return 1.0 + cappedBonus(getWorldScalingStage(world), lootChanceBonusPerWorldStage, maxLootChanceBonus);
    }

    public double getLootExtraAmountChance(World world) {
        return Math.min(maxLootExtraAmountChance, getWorldScalingStage(world) * lootExtraAmountChancePerWorldStage);
    }

    public int getMaxLootExtraAmount() {
        return maxLootExtraAmount;
    }

    private double cappedBonus(int stage, double bonusPerStage, double maxBonus) {
        return Math.min(maxBonus, Math.max(0.0, stage * bonusPerStage));
    }

    public int getDaysPerGearStage() {
        return daysPerGearStage;
    }

    public int getMaxGearStage() {
        return maxGearStage;
    }

    public int getGearStageVariance() {
        return gearStageVariance;
    }

    public boolean isRequireLoadedChunkForSpawn() {
        return requireLoadedChunkForSpawn;
    }

    public int getMaxEventMobsInRadius() {
        return maxEventMobsInRadius;
    }

    public int getEventMobDensityRadius() {
        return eventMobDensityRadius;
    }

    public int getMinDistanceFromBedSpawns() {
        return minDistanceFromBedSpawns;
    }

    public boolean isBiomeDisabled(Biome biome) {
        return disabledBiomes.contains(biome);
    }

    public boolean isDespawnWhenNoPlayersNearby() {
        return despawnWhenNoPlayersNearby;
    }

    public int getNoPlayerDespawnRadius() {
        return noPlayerDespawnRadius;
    }

    public int getNoPlayerDespawnDelaySeconds() {
        return noPlayerDespawnDelaySeconds;
    }

    public boolean isDespawnTargetBoundWhenTargetUnavailable() {
        return despawnTargetBoundWhenTargetUnavailable;
    }

    public int getTargetUnavailableDelaySeconds() {
        return targetUnavailableDelaySeconds;
    }

    public int getTargetUnavailableCombatGraceSeconds() {
        return targetUnavailableCombatGraceSeconds;
    }

    public Set<String> getTargetUnavailableBehaviors() {
        return targetUnavailableBehaviors;
    }

    public Set<String> getTargetUnavailableEvents() {
        return targetUnavailableEvents;
    }

    public boolean isWorldDisabled(String worldName) {
        return disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }
}
