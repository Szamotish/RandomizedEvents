package org.example.randomizedevents.bounty;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.example.randomizedevents.config.BountyOrderDefinition;
import org.example.randomizedevents.config.BountyShopDefinition;
import org.example.randomizedevents.config.EventConfigManager;
import org.example.randomizedevents.spawn.EventSpawner;
import org.example.randomizedevents.spawn.SpawnResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class BountyShopService implements Listener {

    private static final String ORDER_MENU_TITLE = ChatColor.DARK_RED + "Hit Broker";
    private static final String TARGET_MENU_TITLE = ChatColor.DARK_RED + "Choose Target";
    private static final int TARGETS_PER_PAGE = 45;

    private final JavaPlugin plugin;
    private final EventConfigManager config;
    private final EventSpawner spawner;
    private final File file;
    private final Map<UUID, KnownPlayer> knownPlayers = new HashMap<>();
    private final Map<UUID, Bounty> bounties = new HashMap<>();
    private final Map<UUID, String> selectedOrderByPlayer = new HashMap<>();
    private final Map<UUID, Integer> targetPageByPlayer = new HashMap<>();

    private BukkitTask task;
    private Location shopLocation;
    private UUID shopkeeperId;
    private final NamespacedKey shopkeeperKey;
    private final NamespacedKey targetKey;

    public BountyShopService(JavaPlugin plugin, EventConfigManager config, EventSpawner spawner) {
        this.plugin = plugin;
        this.config = config;
        this.spawner = spawner;
        this.file = new File(plugin.getDataFolder(), "bounty-shop.yml");
        this.shopkeeperKey = new NamespacedKey(plugin, "bounty_shopkeeper");
        this.targetKey = new NamespacedKey(plugin, "bounty_target");
        load();
    }

    public void start() {
        stop();
        registerKnownOfflinePlayers();
        ensureShopkeeper();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickBounties, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        removeShopkeeper();
        save();
    }

    public void reload() {
        save();
        load();
        selectedOrderByPlayer.clear();
        targetPageByPlayer.clear();
        ensureShopkeeper();
    }

    public boolean rebuildShop() {
        shopLocation = null;
        ensureShopkeeper();
        return shopLocation != null;
    }

    public void moveShopHere(Player player) {
        shopLocation = player.getLocation().clone();
        save();
        ensureShopkeeper();
    }

    public void removeShopkeeperAndLocation() {
        removeShopkeeper();
        shopLocation = null;
        save();
    }

    public int knownPlayerCount() {
        return knownPlayers.size();
    }

    public int activeBountyCount() {
        return bounties.size();
    }

    public Location shopLocation() {
        return shopLocation == null ? null : shopLocation.clone();
    }

    public boolean hasActiveShopkeeper() {
        return shopkeeperId != null && Bukkit.getEntity(shopkeeperId) != null;
    }

    private void registerKnownOfflinePlayers() {
        for (org.bukkit.OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null) {
                knownPlayers.put(player.getUniqueId(), new KnownPlayer(player.getUniqueId(), player.getName(), System.currentTimeMillis()));
            }
        }
        save();
    }

    private void ensureShopkeeper() {
        BountyShopDefinition shop = config.getBountyShop();
        removeShopkeeper();
        if (shop == null || !shop.enabled()) {
            return;
        }

        if (shopLocation == null || shopLocation.getWorld() == null) {
            shopLocation = findShopLocation(shop);
            if (shopLocation == null) {
                plugin.getLogger().warning("Could not find a location for the bounty shop.");
                return;
            }
            save();
        }

        if (shop.buildStructure()) {
            buildShopStructure(shopLocation);
        }

        Entity spawned = shopLocation.getWorld().spawnEntity(shopLocation, shop.shopkeeperType());
        if (!(spawned instanceof LivingEntity shopkeeper)) {
            spawned.remove();
            plugin.getLogger().warning("Bounty shopkeeper type is not a living entity.");
            return;
        }

        shopkeeperId = shopkeeper.getUniqueId();
        shopkeeper.getPersistentDataContainer().set(shopkeeperKey(), PersistentDataType.INTEGER, 1);
        shopkeeper.setCustomName(config.color(shop.shopkeeperName()));
        shopkeeper.setCustomNameVisible(true);
        shopkeeper.setPersistent(true);
        shopkeeper.setRemoveWhenFarAway(false);
        shopkeeper.setCanPickupItems(false);
        shopkeeper.setInvulnerable(true);
        shopkeeper.setAI(false);
        if (shopkeeper instanceof WanderingTrader trader) {
            trader.setDespawnDelay(Integer.MAX_VALUE);
        }
    }

    private org.bukkit.NamespacedKey shopkeeperKey() {
        return shopkeeperKey;
    }

    private void removeShopkeeper() {
        if (shopkeeperId != null) {
            Entity entity = Bukkit.getEntity(shopkeeperId);
            if (entity != null) {
                entity.remove();
            }
            shopkeeperId = null;
        }

        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (isShopkeeper(entity)) {
                    entity.remove();
                }
            }
        }
    }

    private boolean isShopkeeper(Entity entity) {
        return entity.getPersistentDataContainer().has(shopkeeperKey(), PersistentDataType.INTEGER);
    }

    private Location findShopLocation(BountyShopDefinition shop) {
        World world = shop.worldName() == null || shop.worldName().isBlank()
                ? Bukkit.getWorlds().stream().findFirst().orElse(null)
                : Bukkit.getWorld(shop.worldName());
        if (world == null) {
            return null;
        }

        Location spawn = world.getSpawnLocation();
        int radius = shop.searchRadius();
        for (int attempt = 0; attempt < 300; attempt++) {
            double angle = Math.random() * Math.PI * 2.0;
            int distance = 24 + (int) Math.round(Math.random() * Math.max(1, radius - 24));
            int x = spawn.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = spawn.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            Location location = findSurfaceLocation(world, x, z);
            if (location != null && hasRoomForShop(location)) {
                return location;
            }
        }
        return findSurfaceLocation(world, spawn.getBlockX() + 12, spawn.getBlockZ() + 12);
    }

    private Location findSurfaceLocation(World world, int x, int z) {
        Block topBlock = world.getHighestBlockAt(x, z);
        Block ground = topBlock.getRelative(0, -1, 0);
        Block body = ground.getRelative(0, 1, 0);
        Block head = ground.getRelative(0, 2, 0);
        if (!ground.getType().isSolid() || !body.getType().isAir() || !head.getType().isAir()) {
            return null;
        }
        if (ground.isLiquid() || body.isLiquid() || head.isLiquid()) {
            return null;
        }
        return new Location(world, x + 0.5, body.getY(), z + 0.5);
    }

    private boolean hasRoomForShop(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return false;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block foot = world.getBlockAt(center.getBlockX() + dx, center.getBlockY(), center.getBlockZ() + dz);
                Block head = foot.getRelative(0, 1, 0);
                if (!foot.getType().isAir() || !head.getType().isAir()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void buildShopStructure(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        int x = center.getBlockX();
        int y = center.getBlockY();
        int z = center.getBlockZ();
        placeIfAir(world, x - 2, y, z - 2, Material.OAK_FENCE);
        placeIfAir(world, x + 2, y, z - 2, Material.OAK_FENCE);
        placeIfAir(world, x - 2, y, z + 2, Material.OAK_FENCE);
        placeIfAir(world, x + 2, y, z + 2, Material.OAK_FENCE);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                placeIfAir(world, x + dx, y + 3, z + dz, (dx + dz) % 2 == 0 ? Material.RED_WOOL : Material.WHITE_WOOL);
            }
        }
        placeIfAir(world, x - 1, y, z + 1, Material.BARREL);
        placeIfAir(world, x + 1, y, z + 1, Material.CAMPFIRE);
    }

    private void placeIfAir(World world, int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType().isAir()) {
            block.setType(material, false);
        }
    }

    @EventHandler
    public void onKnownPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        knownPlayers.put(player.getUniqueId(), new KnownPlayer(player.getUniqueId(), player.getName(), System.currentTimeMillis()));
        save();
    }

    @EventHandler
    public void onShopkeeperInteract(PlayerInteractEntityEvent event) {
        if (!isShopkeeper(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        openOrderMenu(event.getPlayer());
    }

    @EventHandler
    public void onShopkeeperDamage(EntityDamageEvent event) {
        if (isShopkeeper(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String title = event.getView().getTitle();
        if (!ORDER_MENU_TITLE.equals(title) && !TARGET_MENU_TITLE.equals(title)) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (ORDER_MENU_TITLE.equals(title)) {
            handleOrderClick(player, event.getRawSlot());
            return;
        }
        handleTargetClick(player, event.getRawSlot(), event.getCurrentItem());
    }

    private void openOrderMenu(Player player) {
        BountyShopDefinition shop = config.getBountyShop();
        if (shop == null || !shop.enabled() || shop.orders().isEmpty()) {
            player.sendMessage(config.inlineMessage("&cThe Hit Broker is not taking contracts right now."));
            return;
        }

        Inventory inventory = Bukkit.createInventory(null, 9, ORDER_MENU_TITLE);
        for (int i = 0; i < Math.min(9, shop.orders().size()); i++) {
            BountyOrderDefinition order = shop.orders().get(i);
            inventory.setItem(i, orderItem(order));
        }
        player.openInventory(inventory);
    }

    private ItemStack orderItem(BountyOrderDefinition order) {
        ItemStack item = new ItemStack(order.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(config.color(order.displayName()));
            List<String> lore = new ArrayList<>();
            lore.add(config.color("&7Contract: &f" + order.eventId()));
            lore.add(config.color("&7Cost: &f" + formatCost(order.cost())));
            lore.add(config.color("&8Click to choose a target."));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void handleOrderClick(Player player, int slot) {
        BountyShopDefinition shop = config.getBountyShop();
        if (shop == null || slot >= shop.orders().size()) {
            return;
        }

        BountyOrderDefinition order = shop.orders().get(slot);
        selectedOrderByPlayer.put(player.getUniqueId(), order.id());
        targetPageByPlayer.put(player.getUniqueId(), 0);
        openTargetMenu(player, 0);
    }

    private void openTargetMenu(Player player, int page) {
        List<KnownPlayer> targets = targetList(player.getUniqueId());
        int maxPage = Math.max(0, (targets.size() - 1) / TARGETS_PER_PAGE);
        page = Math.max(0, Math.min(maxPage, page));
        targetPageByPlayer.put(player.getUniqueId(), page);

        Inventory inventory = Bukkit.createInventory(null, 54, TARGET_MENU_TITLE);
        int offset = page * TARGETS_PER_PAGE;
        for (int slot = 0; slot < TARGETS_PER_PAGE && offset + slot < targets.size(); slot++) {
            inventory.setItem(slot, targetItem(targets.get(offset + slot)));
        }
        inventory.setItem(45, menuItem(Material.ARROW, "&eBack", List.of("&7Return to contracts.")));
        inventory.setItem(49, menuItem(Material.PAPER, "&7Page " + (page + 1) + "/" + (maxPage + 1), List.of()));
        if (page > 0) {
            inventory.setItem(48, menuItem(Material.ARROW, "&ePrevious", List.of()));
        }
        if (page < maxPage) {
            inventory.setItem(50, menuItem(Material.ARROW, "&eNext", List.of()));
        }
        player.openInventory(inventory);
    }

    private ItemStack targetItem(KnownPlayer target) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + target.name());
            meta.setLore(List.of(config.color("&8Click to place a contract.")));
            meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target.uuid().toString());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(config.color(name));
            meta.setLore(lore.stream().map(config::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void handleTargetClick(Player player, int slot, ItemStack clickedItem) {
        if (slot == 45) {
            openOrderMenu(player);
            return;
        }
        int page = targetPageByPlayer.getOrDefault(player.getUniqueId(), 0);
        List<KnownPlayer> targets = targetList(player.getUniqueId());
        int maxPage = Math.max(0, (targets.size() - 1) / TARGETS_PER_PAGE);
        if (slot == 48 && page > 0) {
            openTargetMenu(player, page - 1);
            return;
        }
        if (slot == 50 && page < maxPage) {
            openTargetMenu(player, page + 1);
            return;
        }
        if (slot >= TARGETS_PER_PAGE) {
            return;
        }

        UUID targetId = targetIdFromItem(clickedItem);
        if (targetId == null) {
            return;
        }

        String orderId = selectedOrderByPlayer.get(player.getUniqueId());
        BountyOrderDefinition order = findOrder(orderId);
        if (order == null) {
            player.closeInventory();
            player.sendMessage(config.inlineMessage("&cThis contract is no longer available."));
            return;
        }

        KnownPlayer target = knownPlayers.get(targetId);
        if (target == null || target.uuid().equals(player.getUniqueId())) {
            player.closeInventory();
            player.sendMessage(config.inlineMessage("&cThat target is no longer available."));
            return;
        }
        placeBounty(player, target, order);
    }

    private UUID targetIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String rawId = item.getItemMeta().getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
        if (rawId == null) {
            return null;
        }
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void placeBounty(Player buyer, KnownPlayer target, BountyOrderDefinition order) {
        BountyShopDefinition shop = config.getBountyShop();
        if (shop == null || activeBountiesForTarget(target.uuid()) >= shop.maxActivePerTarget()) {
            buyer.sendMessage(config.inlineMessage("&cToo many active contracts are already waiting for that target."));
            return;
        }
        if (!hasCost(buyer, order.cost())) {
            buyer.sendMessage(config.inlineMessage("&cYou do not have enough payment for this contract. Cost: &f" + formatCost(order.cost())));
            return;
        }

        removeCost(buyer, order.cost());
        UUID bountyId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        bounties.put(bountyId, new Bounty(bountyId, buyer.getUniqueId(), buyer.getName(), target.uuid(), target.name(),
                order.id(), order.eventId(), now, 0L, 0L));
        save();
        buyer.closeInventory();
        buyer.sendMessage(config.inlineMessage("&7The broker accepts your payment. &c" + target.name() + " &7has been marked."));
    }

    private BountyOrderDefinition findOrder(String orderId) {
        BountyShopDefinition shop = config.getBountyShop();
        if (shop == null || orderId == null) {
            return null;
        }
        return shop.orders().stream()
                .filter(order -> order.id().equals(orderId))
                .findFirst()
                .orElse(null);
    }

    private int activeBountiesForTarget(UUID targetId) {
        int count = 0;
        for (Bounty bounty : bounties.values()) {
            if (bounty.targetId.equals(targetId)) {
                count++;
            }
        }
        return count;
    }

    private List<KnownPlayer> targetList(UUID buyerId) {
        return knownPlayers.values().stream()
                .filter(player -> !player.uuid().equals(buyerId))
                .sorted(Comparator.comparing(KnownPlayer::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private boolean hasCost(Player player, List<ItemStack> cost) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : cost) {
            if (!inventory.containsAtLeast(item, item.getAmount())) {
                return false;
            }
        }
        return true;
    }

    private void removeCost(Player player, List<ItemStack> cost) {
        for (ItemStack item : cost) {
            player.getInventory().removeItem(item.clone());
        }
    }

    private String formatCost(List<ItemStack> cost) {
        return cost.stream()
                .map(item -> item.getAmount() + "x " + item.getType().name().toLowerCase().replace('_', ' '))
                .reduce((left, right) -> left + ", " + right)
                .orElse("free");
    }

    private void tickBounties() {
        BountyShopDefinition shop = config.getBountyShop();
        if (shop == null || !shop.enabled() || bounties.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (Bounty bounty : List.copyOf(bounties.values())) {
            Player target = Bukkit.getPlayer(bounty.targetId);
            if (target == null || !target.isOnline() || target.isDead() || !isValidTargetMode(target)) {
                if (bounty.armAt > 0L || bounty.nextAttemptAt > 0L) {
                    bounty.armAt = 0L;
                    bounty.nextAttemptAt = 0L;
                    save();
                }
                continue;
            }

            if (bounty.armAt <= 0L) {
                bounty.armAt = now + shop.armDelaySeconds() * 1000L;
                bounty.nextAttemptAt = bounty.armAt;
                save();
                continue;
            }
            if (now < bounty.armAt || now < bounty.nextAttemptAt) {
                continue;
            }

            SpawnResult result = spawner.spawnEvent(bounty.eventId, target);
            if (result.success()) {
                bounties.remove(bounty.id);
                save();
                plugin.getLogger().info("Bounty contract '" + bounty.orderId + "' spawned event '" + bounty.eventId
                        + "' against " + target.getName() + ".");
            } else {
                bounty.nextAttemptAt = now + shop.retryIntervalSeconds() * 1000L;
                save();
            }
        }
    }

    private boolean isValidTargetMode(Player target) {
        return !config.isRequireTargetInSurvival()
                || target.getGameMode() == GameMode.SURVIVAL
                || target.getGameMode() == GameMode.ADVENTURE;
    }

    private void load() {
        knownPlayers.clear();
        bounties.clear();
        if (!file.exists()) {
            return;
        }

        FileConfiguration data = YamlConfiguration.loadConfiguration(file);
        loadShopLocation(data);
        ConfigurationSection playersSection = data.getConfigurationSection("known-players");
        if (playersSection != null) {
            for (String rawId : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(rawId);
                    String name = playersSection.getString(rawId + ".name", rawId);
                    long lastSeen = playersSection.getLong(rawId + ".last-seen", 0L);
                    knownPlayers.put(uuid, new KnownPlayer(uuid, name, lastSeen));
                } catch (IllegalArgumentException ignored) {
                    // Ignore manually edited invalid UUIDs.
                }
            }
        }

        ConfigurationSection bountiesSection = data.getConfigurationSection("bounties");
        if (bountiesSection != null) {
            for (String rawId : bountiesSection.getKeys(false)) {
                Bounty bounty = loadBounty(rawId, bountiesSection.getConfigurationSection(rawId));
                if (bounty != null) {
                    bounties.put(bounty.id, bounty);
                }
            }
        }
    }

    private void loadShopLocation(FileConfiguration data) {
        ConfigurationSection section = data.getConfigurationSection("shop-location");
        if (section == null) {
            return;
        }
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return;
        }
        shopLocation = new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), 0.0f);
    }

    private Bounty loadBounty(String rawId, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        try {
            UUID id = UUID.fromString(rawId);
            UUID buyerId = UUID.fromString(section.getString("buyer-id", ""));
            UUID targetId = UUID.fromString(section.getString("target-id", ""));
            return new Bounty(
                    id,
                    buyerId,
                    section.getString("buyer-name", "unknown"),
                    targetId,
                    section.getString("target-name", "unknown"),
                    section.getString("order-id", ""),
                    section.getString("event-id", ""),
                    section.getLong("created-at"),
                    section.getLong("arm-at"),
                    section.getLong("next-at")
            );
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void save() {
        FileConfiguration data = new YamlConfiguration();
        if (shopLocation != null && shopLocation.getWorld() != null) {
            data.set("shop-location.world", shopLocation.getWorld().getName());
            data.set("shop-location.x", shopLocation.getX());
            data.set("shop-location.y", shopLocation.getY());
            data.set("shop-location.z", shopLocation.getZ());
            data.set("shop-location.yaw", shopLocation.getYaw());
        }

        for (KnownPlayer player : knownPlayers.values()) {
            String path = "known-players." + player.uuid();
            data.set(path + ".name", player.name());
            data.set(path + ".last-seen", player.lastSeen());
        }

        for (Bounty bounty : bounties.values()) {
            String path = "bounties." + bounty.id;
            data.set(path + ".buyer-id", bounty.buyerId.toString());
            data.set(path + ".buyer-name", bounty.buyerName);
            data.set(path + ".target-id", bounty.targetId.toString());
            data.set(path + ".target-name", bounty.targetName);
            data.set(path + ".order-id", bounty.orderId);
            data.set(path + ".event-id", bounty.eventId);
            data.set(path + ".created-at", bounty.createdAt);
            data.set(path + ".arm-at", bounty.armAt);
            data.set(path + ".next-at", bounty.nextAttemptAt);
        }

        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save bounty-shop.yml.", ex);
        }
    }

    private record KnownPlayer(UUID uuid, String name, long lastSeen) {
    }

    private static final class Bounty {
        private final UUID id;
        private final UUID buyerId;
        private final String buyerName;
        private final UUID targetId;
        private final String targetName;
        private final String orderId;
        private final String eventId;
        private final long createdAt;
        private long armAt;
        private long nextAttemptAt;

        private Bounty(UUID id, UUID buyerId, String buyerName, UUID targetId, String targetName, String orderId,
                       String eventId, long createdAt, long armAt, long nextAttemptAt) {
            this.id = id;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
            this.targetId = targetId;
            this.targetName = targetName;
            this.orderId = orderId;
            this.eventId = eventId;
            this.createdAt = createdAt;
            this.armAt = armAt;
            this.nextAttemptAt = nextAttemptAt;
        }
    }
}
