package com.paimc.vault.gui;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Loads per-menu layout files from plugins/Vault/gui_layout/. */
public final class GuiLayoutService {
    private final Plugin plugin;
    private final File folder;
    private final DialogGuiService dialogs = new DialogGuiService();

    public GuiLayoutService(Plugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "gui_layout");
        if (!folder.exists()) folder.mkdirs();
        for (String menu : new String[]{"main", "settings", "pay", "bank", "history"}) sync(menu);
    }

    private void sync(String menu) {
        String resource = "gui_layout/" + menu + ".yml";
        File file = new File(folder, menu + ".yml");
        if (file.exists()) return;
        try { plugin.saveResource(resource, false); }
        catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Missing default GUI layout resource: " + resource);
        }
    }

    public YamlConfiguration get(String menu) {
        File file = new File(folder, menu.toLowerCase(Locale.ROOT) + ".yml");
        return YamlConfiguration.loadConfiguration(file);
    }

    public boolean useDialog(String menu) {
        return "DIALOG".equalsIgnoreCase(get(menu).getString("mode", "INVENTORY"));
    }

    public int size(String menu, int fallback) {
        return normalizeSize(get(menu).getInt("inventory.size", fallback));
    }

    public int size(String menu, String key, int fallback) {
        return normalizeSize(get(menu).getInt("inventory." + key, fallback));
    }

    private int normalizeSize(int size) {
        size = Math.max(9, Math.min(54, size));
        return (size / 9) * 9;
    }

    public String title(String menu, String fallback) {
        String configured = get(menu).getString("inventory.title", "");
        return configured == null || configured.isBlank() ? fallback : configured;
    }

    public String dialogTitle(String menu, String fallback) {
        String configured = get(menu).getString("dialog.title", "");
        return configured == null || configured.isBlank() ? fallback : configured;
    }

    public String dialogDescription(String menu, String fallback) {
        String configured = get(menu).getString("dialog.description", "");
        return configured == null || configured.isBlank() ? fallback : configured;
    }

    public int slot(String menu, String item, int fallback) {
        int slot = get(menu).getInt("items." + item + ".slot", fallback);
        return Math.max(0, Math.min(53, slot));
    }

    public int slot(String menu, String item, int fallback, int inventorySize) {
        return Math.max(0, Math.min(Math.max(0, inventorySize - 1), slot(menu, item, fallback)));
    }

    public Material material(String menu, String item, Material fallback) {
        String name = get(menu).getString("items." + item + ".material", fallback.name());
        Material material = Material.matchMaterial(name == null ? "" : name);
        return material == null ? fallback : material;
    }

    public boolean enabled(String menu, String item, boolean fallback) {
        return get(menu).getBoolean("items." + item + ".enabled", fallback);
    }

    public List<String> dialogActions(String menu, List<String> fallback) {
        List<String> actions = get(menu).getStringList("dialog.actions");
        return actions.isEmpty() ? fallback : Collections.unmodifiableList(new ArrayList<>(actions));
    }

    public void showDialog(Player player, String menu, String title, String description,
                           List<DialogGuiService.Action> availableActions) {
        List<String> configuredIds = dialogActions(menu, List.of());
        List<DialogGuiService.Action> ordered = new ArrayList<>();
        for (String id : configuredIds) {
            for (DialogGuiService.Action action : availableActions) {
                // Action IDs are unique and order is controlled by the layout file.
                if (id.equalsIgnoreCase(action.id())) { ordered.add(action); break; }
            }
        }
        if (configuredIds.isEmpty()) ordered.addAll(availableActions);
        dialogs.show(player, title, description, ordered, get(menu).getInt("dialog.columns", 2));
    }


}
