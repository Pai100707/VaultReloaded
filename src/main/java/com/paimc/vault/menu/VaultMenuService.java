package com.paimc.vault.menu;

import com.paimc.vault.VaultPlugin;
import com.paimc.vault.economy.BankService;
import com.paimc.vault.economy.SimpleEconomy;
import com.paimc.vault.i18n.Messages;
import com.paimc.vault.gui.DialogGuiService;
import com.paimc.vault.gui.GuiLayoutService;
import com.paimc.vault.util.ClickablePromptUtil;
import com.paimc.vault.util.ColorUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class VaultMenuService implements Listener {
    private final VaultPlugin plugin;
    private final Messages messages;
    private final BankMenuService bankMenuService;
    private PayMenuService payMenuService;
    private ConfigEditorService configEditorService;
    private final ConcurrentMap<UUID, Map<Integer, String>> textKeysByPlayer = new ConcurrentHashMap<>();

    public VaultMenuService(VaultPlugin plugin, Messages messages, BankService bankService) {
        this.plugin = plugin;
        this.messages = messages;
        this.bankMenuService = new BankMenuService(plugin, messages, bankService);
    }

    public BankMenuService getBankMenuService() { return bankMenuService; }

    public void openBankMenu(Player player) {
        bankMenuService.openBankMenu(player);
    }

    public void setPayMenuService(PayMenuService payMenuService) {
        this.payMenuService = payMenuService;
    }

    public void setConfigEditorService(ConfigEditorService configEditorService) {
        this.configEditorService = configEditorService;
    }

    public boolean cancelAdminEdit(Player player) {
        return configEditorService != null && configEditorService.cancelEdit(player);
    }

    public void openMainMenu(Player player) {
        GuiLayoutService layouts = plugin.getGuiLayouts();
        if (layouts != null && layouts.useDialog("main")) {
            List<DialogGuiService.Action> actions = new ArrayList<>();
            if (layouts.enabled("main", "pay", true)) {
                actions.add(new DialogGuiService.Action("pay", display("vault.menu.item.pay", "vault.menu.item.pay"), null,
                        target -> { if (payMenuService != null) payMenuService.openMainMenu(target); }));
            }
            if (layouts.enabled("main", "bank", true)) {
                actions.add(new DialogGuiService.Action("bank", display("bank.menu.item_name", "bank.menu.item_name"), null,
                        this::openBankMenu));
            }
            if (player.isOp()) {
                if (layouts.enabled("main", "settings", true)) {
                    actions.add(new DialogGuiService.Action("settings", display("vault.menu.item.settings", "vault.menu.item.settings"), null,
                            this::openSettingsMenu));
                }
                if (layouts.enabled("main", "reload", true)) {
                    actions.add(new DialogGuiService.Action("reload", display("vault.menu.item.reload", "vault.menu.item.reload"), null,
                            target -> {
                                plugin.reloadPluginState();
                                target.sendMessage(messages.chat("plugin.reloaded"));
                            }));
                }
            }
            layouts.showDialog(player, "main",
                    layouts.dialogTitle("main", titleMain()),
                    layouts.dialogDescription("main", messages.get("vault.dialog.description")), actions);
            return;
        }
        int size = layouts == null ? 27 : layouts.size("main", 27);
        String title = layouts == null ? titleMain() : ColorUtil.colorize(layouts.title("main", titleMain()));
        Inventory inv = Bukkit.createInventory(null, size, title);

        SimpleEconomy se = null;
        if (plugin.getEconomyProvider() instanceof SimpleEconomy) se = (SimpleEconomy) plugin.getEconomyProvider();
        BankService b = plugin.getBankService();

        int paySlot = layouts == null ? 10 : layouts.slot("main", "pay", 10, size);
        if (layouts == null || layouts.enabled("main", "pay", true))
            inv.setItem(paySlot, item(layouts == null ? Material.EMERALD : layouts.material("main", "pay", Material.EMERALD), display("vault.menu.item.pay", "vault.menu.item.pay"), lore("vault.menu.lore.pay")));

        // 🏦 Bank item (slot 13)
        if (se != null && b != null) {
            String cid = se.getDefaultCurrencyId();
            double bankBal = b.getBankBalance(player.getUniqueId());
            double irate = plugin.getConfig().getDouble("bank.interest.percent_per_period", 0.5);
            Map<String, String> c = new LinkedHashMap<>();
            c.put("bank", se.format(cid, bankBal));
            c.put("interest_next", se.format(cid, Math.max(0, bankBal) * irate / 100.0));
            // Build item with placeholders in lore
            String bankName = messages.getOptional("bank.menu.item_name");
            if (bankName == null || bankName.isEmpty()) bankName = "bank.menu.item_name";
            String dispName = replaceAndColor(bankName, c);
            List<String> baseLore = messages.formatList("bank.menu.item_lore", c);
            List<String> coloredLore = new ArrayList<>(baseLore.size());
            for (String l : baseLore) coloredLore.add(ColorUtil.colorize(l));
            Material gold = Material.matchMaterial("GOLD_BLOCK");
            if (gold == null) gold = Material.GOLD_BLOCK;
            if (layouts == null || layouts.enabled("main", "bank", true))
                inv.setItem(layouts == null ? 13 : layouts.slot("main", "bank", 13, size), item(layouts == null ? gold : layouts.material("main", "bank", gold), dispName, coloredLore));
        }

        if (player.isOp()) {
            Material comparator = Material.matchMaterial("COMPARATOR");
            if (comparator == null) comparator = Material.matchMaterial("REDSTONE_COMPARATOR");
            if (comparator == null) comparator = Material.REDSTONE;
            if (layouts == null || layouts.enabled("main", "settings", true))
                inv.setItem(layouts == null ? 14 : layouts.slot("main", "settings", 14, size), item(layouts == null ? comparator : layouts.material("main", "settings", comparator), display("vault.menu.item.settings", "vault.menu.item.settings"), lore("vault.menu.lore.settings")));
            if (layouts == null || layouts.enabled("main", "reload", true))
                inv.setItem(layouts == null ? 16 : layouts.slot("main", "reload", 16, size), item(layouts == null ? Material.BOOK : layouts.material("main", "reload", Material.BOOK), display("vault.menu.item.reload", "vault.menu.item.reload"), lore("vault.menu.lore.reload")));
        }

        player.openInventory(inv);
    }

    public void openSettingsMenu(Player player) {
        GuiLayoutService layouts = plugin.getGuiLayouts();
        if (layouts != null && layouts.useDialog("settings")) {
            List<DialogGuiService.Action> actions = new ArrayList<>();
            if (layouts.enabled("settings", "pay", true))
                actions.add(new DialogGuiService.Action("pay", display("settings.item.pay", "settings.item.pay"), null, this::openPaySettingsMenu));
            if (layouts.enabled("settings", "texts", true))
                actions.add(new DialogGuiService.Action("texts", display("settings.item.texts", "settings.item.texts"), null, this::openTextSettingsMenu));
            if (layouts.enabled("settings", "back", true))
                actions.add(new DialogGuiService.Action("back", display("settings.item.back", "settings.item.back"), null, this::openMainMenu));
            layouts.showDialog(player, "settings",
                    layouts.dialogTitle("settings", titleSettingsMain()),
                    layouts.dialogDescription("settings", messages.get("settings.dialog.description")), actions);
            return;
        }
        int size = layouts == null ? 27 : layouts.size("settings", 27);
        Inventory inv = Bukkit.createInventory(null, size,
                layouts == null ? titleSettingsMain() : ColorUtil.colorize(layouts.title("settings", titleSettingsMain())));
        if (layouts == null || layouts.enabled("settings", "pay", true))
            inv.setItem(layouts == null ? 12 : layouts.slot("settings", "pay", 12, size), item(layouts == null ? Material.EMERALD : layouts.material("settings", "pay", Material.EMERALD), display("settings.item.pay", "settings.item.pay"), lore("settings.lore.pay")));
        if (layouts == null || layouts.enabled("settings", "texts", true))
            inv.setItem(layouts == null ? 14 : layouts.slot("settings", "texts", 14, size), item(layouts == null ? Material.NAME_TAG : layouts.material("settings", "texts", Material.NAME_TAG), display("settings.item.texts", "settings.item.texts"), lore("settings.lore.texts")));
        if (layouts == null || layouts.enabled("settings", "back", true))
            inv.setItem(layouts == null ? 16 : layouts.slot("settings", "back", 16, size), item(layouts == null ? Material.ARROW : layouts.material("settings", "back", Material.ARROW), display("settings.item.back", "settings.item.back"), lore("settings.lore.back")));
        player.openInventory(inv);
    }


    public void openPaySettingsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, titleSettingsPay());

        GuiLayoutService layouts = plugin.getGuiLayouts();
        if (layouts == null || layouts.enabled("settings", "pay_min", true))
            inv.setItem(layouts == null ? 11 : layouts.slot("settings", "pay_min", 11, inv.getSize()), item(layouts == null ? Material.GOLD_NUGGET : layouts.material("settings", "pay_min", Material.GOLD_NUGGET), messages.color("settings.pay.min") + " &7(" + plugin.getConfig().getDouble("pay_limits.min", 0.0) + ")", lore("settings.lore.pay_min")));
        if (layouts == null || layouts.enabled("settings", "pay_max", true))
            inv.setItem(layouts == null ? 12 : layouts.slot("settings", "pay_max", 12, inv.getSize()), item(layouts == null ? Material.GOLD_INGOT : layouts.material("settings", "pay_max", Material.GOLD_INGOT), messages.color("settings.pay.max") + " &7(" + plugin.getConfig().getDouble("pay_limits.max", 0.0) + ")", lore("settings.lore.pay_max")));
        if (layouts == null || layouts.enabled("settings", "pay_menu_size", true))
            inv.setItem(layouts == null ? 13 : layouts.slot("settings", "pay_menu_size", 13, inv.getSize()), item(layouts == null ? Material.CHEST : layouts.material("settings", "pay_menu_size", Material.CHEST), messages.color("settings.pay.menu_size") + " &7(" + plugin.getConfig().getInt("pay_menu.size", 27) + ")", lore("settings.lore.pay_menu_size")));
        Material head = Material.matchMaterial("PLAYER_HEAD");
        if (head == null) head = Material.matchMaterial("SKULL_ITEM");
        if (head == null) head = Material.PAPER;
        if (layouts == null || layouts.enabled("settings", "pay_show_self", true))
            inv.setItem(layouts == null ? 14 : layouts.slot("settings", "pay_show_self", 14, inv.getSize()), item(layouts == null ? head : layouts.material("settings", "pay_show_self", head), messages.color("settings.pay.show_self") + " &7(" + plugin.getConfig().getBoolean("pay_menu.show_self", false) + ")", lore("settings.lore.pay_show_self")));
        if (layouts == null || layouts.enabled("settings", "back", true))
            inv.setItem(layouts == null ? 22 : layouts.slot("settings", "back", 22, inv.getSize()), item(layouts == null ? Material.ARROW : layouts.material("settings", "back", Material.ARROW), display("settings.item.back", "settings.item.back"), lore("settings.lore.back")));
        player.openInventory(inv);
    }

    public void openTextSettingsMenu(Player player) {
        GuiLayoutService layouts = plugin.getGuiLayouts();
        int size = layouts == null ? 54 : layouts.size("settings", "texts_size", 54);
        Inventory inv = Bukkit.createInventory(null, size, titleSettingsTexts());
        java.util.Map<Integer, String> map = new java.util.HashMap<>();

        int slot = 0;
        slot = addTextItem(inv, map, slot, Material.PAPER, "vault.menu.title");
        slot = addTextItem(inv, map, slot, Material.PAPER, "vault.menu.item.pay");
        slot = addTextItem(inv, map, slot, Material.PAPER, "vault.menu.item.settings");
        slot = addTextItem(inv, map, slot, Material.PAPER, "vault.menu.item.reload");
        slot = addTextItem(inv, map, slot, Material.BOOK, "pay.menu.title_main");
        slot = addTextItem(inv, map, slot, Material.BOOK, "pay.menu.item.pay");
        slot = addTextItem(inv, map, slot, Material.BOOK, "pay.menu.item.view_balance");
        slot = addTextItem(inv, map, slot, Material.BOOK, "pay.menu.item.charge");

        if (layouts == null || layouts.enabled("settings", "text_back", true))
            inv.setItem(layouts == null ? Math.min(53, size - 1) : layouts.slot("settings", "text_back", Math.min(53, size - 1), size),
                    item(layouts == null ? Material.ARROW : layouts.material("settings", "text_back", Material.ARROW), display("settings.item.back", "settings.item.back"), lore("settings.lore.back")));
        textKeysByPlayer.put(player.getUniqueId(), map);
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
        String title = event.getView().getTitle();
        GuiLayoutService layouts = plugin.getGuiLayouts();
        String configuredMainTitle = layouts == null ? titleMain() : ColorUtil.colorize(layouts.title("main", titleMain()));
        String configuredSettingsTitle = layouts == null ? titleSettingsMain() : ColorUtil.colorize(layouts.title("settings", titleSettingsMain()));
        boolean isMain = title.equals(configuredMainTitle);
        boolean isSettingsMain = title.equals(configuredSettingsTitle);
        boolean isSettingsPay = title.equals(titleSettingsPay());
        boolean isSettingsTexts = title.equals(titleSettingsTexts());
        if (!(isMain || isSettingsMain || isSettingsPay || isSettingsTexts)) return;
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        String name = meta != null ? meta.getDisplayName() : "";

        if (isMain) {
            if (name.equals(display("vault.menu.item.pay", "vault.menu.item.pay"))) {
                if (payMenuService != null) {
                    payMenuService.openMainMenu(player);
                }
                return;
            }
            // Bank menu item matches on placeholder-resolved bank.menu.item_name raw
            String rawBankName = messages.getOptional("bank.menu.item_name");
            String fallbackBankName = messages.get("bank.menu.item_name");
            SimpleEconomy se = null;
            if (plugin.getEconomyProvider() instanceof SimpleEconomy) se = (SimpleEconomy) plugin.getEconomyProvider();
            BankService bs = plugin.getBankService();
            boolean bankMatch = false;
            if (se != null && bs != null && rawBankName != null && !rawBankName.isEmpty()) {
                Map<String, String> c = new LinkedHashMap<>();
                String cid = se.getDefaultCurrencyId();
                c.put("bank", se.format(cid, bs.getBankBalance(player.getUniqueId())));
                double irate = plugin.getConfig().getDouble("bank.interest.percent_per_period", 0.5);
                c.put("interest_next", se.format(cid, Math.max(0, bs.getBankBalance(player.getUniqueId())) * irate / 100.0));
                bankMatch = name.equals(replaceAndColor(rawBankName, c));
            }
            if (!bankMatch && (name.equals(ColorUtil.colorize(fallbackBankName)))) bankMatch = true;
            if (bankMatch) {
                openBankMenu(player);
                return;
            }
            if (player.isOp() && name.equals(display("vault.menu.item.settings", "vault.menu.item.settings"))) {
                openSettingsMenu(player);
                return;
            }
            if (player.isOp() && name.equals(display("vault.menu.item.reload", "vault.menu.item.reload"))) {
                plugin.reloadPluginState();
                String lang = plugin.getConfig().getString("language", "en");
                player.sendMessage(messages.formatChat("plugin.reloaded", java.util.Collections.singletonMap("lang", lang)));
                player.closeInventory();
                return;
            }
            return;
        }

        if (!player.isOp()) return;

        if (isSettingsMain) {
            if (name.equals(display("settings.item.pay", "settings.item.pay"))) {
                openPaySettingsMenu(player);
                return;
            }
            if (name.equals(display("settings.item.texts", "settings.item.texts"))) {
                openTextSettingsMenu(player);
                return;
            }
            if (name.equals(display("settings.item.back", "settings.item.back"))) {
                openMainMenu(player);
                return;
            }
        }


        if (isSettingsPay) {
            if (name.startsWith(ColorUtil.colorize(messages.get("settings.pay.min")))) {
                startConfigEdit(player, "pay_limits.min", ConfigEditorService.EditType.CONFIG_DOUBLE, "vault_settings_pay");
                return;
            }
            if (name.startsWith(ColorUtil.colorize(messages.get("settings.pay.max")))) {
                startConfigEdit(player, "pay_limits.max", ConfigEditorService.EditType.CONFIG_DOUBLE, "vault_settings_pay");
                return;
            }
            if (name.startsWith(ColorUtil.colorize(messages.get("settings.pay.menu_size")))) {
                startConfigEdit(player, "pay_menu.size", ConfigEditorService.EditType.CONFIG_INT, "vault_settings_pay");
                return;
            }
            if (name.startsWith(ColorUtil.colorize(messages.get("settings.pay.show_self")))) {
                boolean v = !plugin.getConfig().getBoolean("pay_menu.show_self", false);
                plugin.getConfig().set("pay_menu.show_self", v);
                plugin.saveConfig();
                plugin.reloadPluginState();
                openPaySettingsMenu(player);
                return;
            }
            if (name.equals(display("settings.item.back", "settings.item.back"))) {
                openSettingsMenu(player);
                return;
            }
        }

        if (isSettingsTexts) {
            if (name.equals(display("settings.item.back", "settings.item.back"))) {
                openSettingsMenu(player);
                return;
            }
            java.util.Map<Integer, String> map = textKeysByPlayer.get(player.getUniqueId());
            if (map == null) return;
            String key = map.get(event.getSlot());
            if (key == null || key.isEmpty()) return;
            startMessageEdit(player, key, "vault_settings_texts");
        }
    }

    private void startConfigEdit(Player player, String key, ConfigEditorService.EditType type, String back) {
        if (configEditorService == null) return;
        player.closeInventory();
        configEditorService.startEdit(player, type, key, back);
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("key", key);
        ClickablePromptUtil.sendPromptWithClickableCancel(player, messages, "settings.prompt.value", m,
                "cancel", ClickEvent.Action.RUN_COMMAND, "/vault cancel");
    }

    private void startMessageEdit(Player player, String key, String back) {
        if (configEditorService == null) return;
        player.closeInventory();
        configEditorService.startEdit(player, ConfigEditorService.EditType.MESSAGE_STRING, key, back);
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("key", key);
        ClickablePromptUtil.sendPromptWithClickableCancel(player, messages, "settings.prompt.text", m,
                "cancel", ClickEvent.Action.RUN_COMMAND, "/vault cancel");
    }

    private int addTextItem(Inventory inv, java.util.Map<Integer, String> map, int slot, Material mat, String key) {
        if (slot >= inv.getSize() - 1) return slot;
        String current = messages.getOptional(key);
        if (current == null || current.isEmpty()) current = key;
        String name = current;
        java.util.List<String> lore = new java.util.ArrayList<>();
        java.util.Map<String, String> loreValues = new java.util.HashMap<>();
        loreValues.put("key", key);
        loreValues.put("current", current);
        lore.add(messages.format("settings.text_editor.key", loreValues));
        lore.add(messages.format("settings.text_editor.current", loreValues));
        ItemStack it = item(mat, ColorUtil.colorize(name), translateLore(lore));
        inv.setItem(slot, it);
        map.put(slot, key);
        return slot + 1;
    }

    private java.util.List<String> translateLore(java.util.List<String> lore) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String l : lore) out.add(ColorUtil.colorize(l));
        return out;
    }

    private String titleMain() {
        return ColorUtil.colorize(messages.get("vault.menu.title"));
    }

    private String titleSettingsMain() {
        return ColorUtil.colorize(messages.get("settings.title"));
    }


    private String titleSettingsPay() {
        return ColorUtil.colorize(messages.get("settings.title_pay"));
    }

    private String titleSettingsTexts() {
        return ColorUtil.colorize(messages.get("settings.title_texts"));
    }

    private String replaceAndColor(String raw, Map<String, String> ctx) {
        if (raw == null) return "";
        String r = raw;
        for (Map.Entry<String, String> e : ctx.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            r = r.replace("%" + e.getKey() + "%", v);
        }
        return ColorUtil.colorize(r);
    }

    private String display(String key, String fallback) {
        String raw = messages.getOptional(key);
        if (raw == null || raw.isEmpty()) {
            String alt = alternateKey(key);
            if (!alt.equals(key)) raw = messages.getOptional(alt);
        }
        if (raw == null || raw.isEmpty()) raw = fallback;
        return ColorUtil.colorize(raw);
    }

    private java.util.List<String> lore(String key) {
        java.util.List<String> list = messages.colorList(key);
        if (!list.isEmpty()) return list;
        String alt = alternateKey(key);
        if (!alt.equals(key)) return messages.colorList(alt);
        return java.util.Collections.emptyList();
    }

    private String alternateKey(String key) {
        if (key.contains(".items.")) return key.replace(".items.", ".item.");
        if (key.contains(".item.")) return key.replace(".item.", ".items.");
        return key;
    }

    private ItemStack item(Material mat, String displayName, java.util.List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(ColorUtil.colorize(displayName));
            if (lore != null && !lore.isEmpty()) {
                java.util.List<String> out = new java.util.ArrayList<>(lore.size());
                for (String s : lore) {
                    if (s == null) continue;
                    out.add(ColorUtil.colorize(s));
                }
                im.setLore(out);
            }
            it.setItemMeta(im);
        }
        return it;
    }
}
