package com.paimc.vault.util;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.Server;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Safely resolves players without relying on deprecated APIs.
 */
public final class PlayerResolver {
    private PlayerResolver() {}

    /**
     * Resolve a player by name, preferring online players and then checking
     * offline players known to the server. Returns null when no match exists.
     */
    public static OfflinePlayer resolveByName(Plugin plugin, String name) {
        if (name == null || name.isEmpty()) return null;
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer p : plugin.getServer().getOfflinePlayers()) {
            String n = p.getName();
            if (n != null && n.equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    public static OfflinePlayer resolveByName(org.bukkit.Server server, String name) {
        if (name == null || name.isEmpty()) return null;
        Player online = server.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer p : server.getOfflinePlayers()) {
            String n = p.getName();
            if (n != null && n.equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Like resolveByName, but when the server is in offline mode and the
     * player cannot be found, creates a deterministic offline UUID and
     * returns the OfflinePlayer associated with that UUID.
     */
    public static OfflinePlayer resolveByNameWithOfflineFallback(Server server, String name) {
        OfflinePlayer found = resolveByName(server, name);
        if (found != null) return found;
        if (!server.getOnlineMode()) {
            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            return server.getOfflinePlayer(offlineUuid);
        }
        return null;
    }

    public static OfflinePlayer resolveByNameWithOfflineFallback(Plugin plugin, String name) {
        org.bukkit.Server server = plugin.getServer();
        OfflinePlayer found = resolveByName(server, name);
        if (found != null) return found;
        boolean allowFallback = true;
        try {
            if (plugin instanceof org.bukkit.plugin.java.JavaPlugin) {
                org.bukkit.plugin.java.JavaPlugin jp = (org.bukkit.plugin.java.JavaPlugin) plugin;
                allowFallback = jp.getConfig().getBoolean("offline-uuid-fallback", true);
            }
        } catch (Exception ignored) {}
        if (!server.getOnlineMode() && allowFallback) {
            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            return server.getOfflinePlayer(offlineUuid);
        }
        return null;
    }
}