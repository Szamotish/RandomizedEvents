package org.example.randomizedevents.loot;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class TokenService {

    private final NamespacedKey bossTokenKey;
    private final NamespacedKey tokenIdKey;

    public TokenService(JavaPlugin plugin) {
        this.bossTokenKey = new NamespacedKey(plugin, "boss_token");
        this.tokenIdKey = new NamespacedKey(plugin, "boss_token_id");
    }

    public ItemStack createBossToken(String tokenId, String displayName) {
        ItemStack token = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = token.getItemMeta();
        if (meta == null) {
            return token;
        }

        meta.setDisplayName(displayName);
        meta.setLore(List.of("A sealed proof of an event boss kill.", "Keep it, trade it, or submit it."));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(bossTokenKey, PersistentDataType.INTEGER, 1);
        meta.getPersistentDataContainer().set(tokenIdKey, PersistentDataType.STRING, tokenId);
        token.setItemMeta(meta);
        return token;
    }

    public boolean isBossToken(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(bossTokenKey, PersistentDataType.INTEGER);
    }

    public String getTokenId(ItemStack item) {
        if (!isBossToken(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(tokenIdKey, PersistentDataType.STRING);
    }
}
