package com.paimc.vault;

import com.paimc.vault.commands.BalanceCommand;
import com.paimc.vault.commands.EcoCommand;
import com.paimc.vault.commands.PayCommand;
import com.paimc.vault.commands.VaultCommand;
import com.paimc.vault.commands.VaultOpCommand;
import com.paimc.vault.economy.BankService;
import com.paimc.vault.economy.OfflinePayQueueService;
import com.paimc.vault.economy.PhysicalNoteService;
import com.paimc.vault.economy.SimpleEconomy;
import com.paimc.vault.economy.TopCacheService;
import com.paimc.vault.notifications.DiscordWebhookNotifier;
import com.paimc.vault.menu.HistoryMenuService;
import com.paimc.vault.menu.PayMenuService;
import com.paimc.vault.i18n.Messages;
import com.paimc.vault.gui.GuiLayoutService;
import com.paimc.vault.menu.ChargeRequestService;
import com.paimc.vault.menu.ConfigEditorService;
import com.paimc.vault.menu.VaultMenuService;
import com.paimc.vault.transactions.TransactionLogService;
import com.paimc.vault.storage.balance.BalanceStorage;
import com.paimc.vault.storage.balance.BalanceStorageFactory;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;
import org.bstats.bukkit.Metrics;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class VaultPlugin extends JavaPlugin {
    private Economy economy;
    private PayMenuService payMenuService;
    private Messages messages;
    private GuiLayoutService guiLayouts;
    private VaultMenuService vaultMenuService;
    private ConfigEditorService configEditorService;
    private TransactionLogService txLog;
    private TopCacheService topCache;
    private OfflinePayQueueService offlinePay;
    private PhysicalNoteService noteService;
    private BankService bankService;
    private HistoryMenuService historyMenu;
    private DiscordWebhookNotifier discord;
    private ScheduledTask autosaveTask;

    public Economy getEconomyProvider() {
        return economy;
    }

    public TransactionLogService getTxLog() {
        return txLog;
    }

    public Messages getMessages() { return messages; }
    public GuiLayoutService getGuiLayouts() { return guiLayouts; }
    public BankService getBankService() { return bankService; }
    public TopCacheService getTopCache() { return topCache; }

    @Override
    public void onEnable() {
        // Ensure data folder
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        // Recreate missing config.yml or merge in any new default keys.
        syncConfigFile();

        // Load messages based on config language
        String lang = getConfig().getString("language", "en");
        messages = new Messages(this, lang);
        guiLayouts = new GuiLayoutService(this);

        boolean useMySQL = getConfig().getBoolean("storage.use_mysql", false);
        com.paimc.vault.storage.Database db = null;
        SimpleEconomy provider = new SimpleEconomy(this);

        // This legacy database remains the backend for transaction logs and charge requests.
        // Balance persistence is handled independently by the selectable BalanceStorage backend.
        if (useMySQL) {
            try {
                db = new com.paimc.vault.storage.Database(this);
                db.ensureSchema();
                provider.setDatabase(db);
            } catch (java.sql.SQLException ex) {
                getLogger().severe("Failed to initialize legacy MySQL transaction storage: " + ex.getMessage());
                db = null;
            }
        }

        try {
            BalanceStorage balanceStorage = BalanceStorageFactory.create(this);
            provider.setBalanceStorage(balanceStorage);
            provider.load();
            getLogger().info("Balance storage initialized: " + balanceStorage.type());
        } catch (Exception ex) {
            getLogger().severe("Could not initialize balance storage. Check storage.balances in config.yml: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Import Essentials balances once when enabled in config.
        if (getConfig().getBoolean("import.essentials.enabled", false)) {
            boolean replace = getConfig().getBoolean("import.essentials.replace", false);
            int mode = replace ? 1 : 0; // 0=merge, 1=replace
            new com.paimc.vault.importer.EssentialsImportService(this, provider).runOnce(mode);
        }
        txLog = new TransactionLogService(this, db);
        provider.setTransactionLogService(txLog);

        // --- Services ---
        topCache = new TopCacheService(this, provider);
        topCache.start(getConfig().getLong("top.refresh_seconds", 300L));
        offlinePay = new OfflinePayQueueService(this, provider);
        noteService = new PhysicalNoteService(this, provider, messages);
        bankService = new BankService(this, provider);
        bankService.start();
        historyMenu = new HistoryMenuService(this, messages);
        discord = new DiscordWebhookNotifier(this, provider, messages);
        txLog.addFlushListener(discord::onTransactionBatch);
        txLog.addDupeListener(discord::onDupeDetected);
        getServer().getPluginManager().registerEvents(offlinePay, this);
        getServer().getPluginManager().registerEvents(noteService, this);
        getServer().getPluginManager().registerEvents(historyMenu, this);

        this.economy = provider;
        getServer().getServicesManager().register(Economy.class, provider, this, ServicePriority.Highest);
        // Do not call reloadCurrencyFormat here: the constructor already ran loadCurrencyDefinitions() and
        // load() has just populated currencyData.balances from storage.
        // a second loadCurrencyDefinitions call would create empty CurrencyData objects and erase the loaded balances.

        // Schedule autosave
        scheduleAutosave(provider);

        // Register PlaceholderAPI expansion if plugin is present
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.paimc.vault.placeholder.VaultPlaceholderExpansion(this, economy, messages).register();
            new com.paimc.vault.placeholder.Vault2PlaceholderExpansion(this, economy, messages).register();
            getLogger().info("PlaceholderAPI expansions registered: %vault_*% and %vault2_*%.");
        }

        vaultMenuService = new VaultMenuService(this, messages, bankService);
        getServer().getPluginManager().registerEvents(vaultMenuService, this);
        configEditorService = new ConfigEditorService(this, messages);
        getServer().getPluginManager().registerEvents(configEditorService, this);
        vaultMenuService.setConfigEditorService(configEditorService);

        // CMI integration removed per request.

        // Register commands using our Economy
        PayCommand payCmd = null;
        VaultCommand vaultCmd = null;
        EcoCommand ecoCmd = null;

        if (getCommand("balance") != null) {
            getCommand("balance").setExecutor(new BalanceCommand(this, economy, messages));
        }
        if (getCommand("pay") != null) {
            // After economy initialization
            ChargeRequestService chargeRequestService = new ChargeRequestService(this, economy, messages, db);
            getServer().getPluginManager().registerEvents(chargeRequestService, this);
            payMenuService = new PayMenuService(this, economy, messages, chargeRequestService);
            getServer().getPluginManager().registerEvents(payMenuService, this);
            // Register commands
            payCmd = new PayCommand(this, economy, payMenuService, messages);
            getCommand("pay").setExecutor(payCmd);
            if (vaultMenuService != null) {
                vaultMenuService.setPayMenuService(payMenuService);
            }
        }
        if (getCommand("vault") != null) {
            vaultCmd = new VaultCommand(this, messages, vaultMenuService);
            getCommand("vault").setExecutor(vaultCmd);
        }
        if (getCommand("vaultop") != null) {
            getCommand("vaultop").setExecutor(new VaultOpCommand(this, economy, messages));
        }
        // Register /eco admin command (give/take)
        if (getCommand("eco") != null) {
            ecoCmd = new EcoCommand(this, economy, messages);
            getCommand("eco").setExecutor(ecoCmd);
        }

        // --- WIRING SERVICES -> COMMANDS ---
        if (ecoCmd != null) {
            ecoCmd.setTopCache(topCache);
        }
        if (payCmd != null) {
            payCmd.setOfflinePayQueue(offlinePay);
        }
        if (vaultCmd != null) {
            vaultCmd.topCache = topCache;
            vaultCmd.offlinePay = offlinePay;
            vaultCmd.noteService = noteService;
            vaultCmd.bankService = bankService;
            vaultCmd.historyMenu = historyMenu;
        }

        // Initialize bStats Metrics
        // IMPORTANT: Replace 12345 with your own plugin ID from https://bstats.org
        int pluginId = 28342;
        Metrics metrics = new Metrics(this, pluginId);

        // Custom charts
        metrics.addCustomChart(new org.bstats.charts.SimplePie("storage_type", provider::getBalanceStorageType));
        
        metrics.addCustomChart(new org.bstats.charts.SimplePie("language", () -> 
            getConfig().getString("language", "en")
        ));

        com.paimc.vault.util.LogoPrinter.printEnable(this);
        getLogger().info(messages.get("plugin.enabled"));
    }

    public void reloadPluginState() {
        // Recreate missing config.yml or merge in any new default keys.
        syncConfigFile();
        String lang = getConfig().getString("language", "en");
        messages.reload(lang);
        // Reschedule autosave with new config
        if (economy instanceof SimpleEconomy) {
            SimpleEconomy se = (SimpleEconomy) economy;
            // First persist current balances (YAML/JSON or SQL) in case there are
            // in-memory changes that have not reached storage since the last autosave.
            try {
                se.saveAllNow();
            } catch (java.io.IOException ex) {
                getLogger().warning("Failed to save balances before /vault reload: " + ex.getMessage());
            }
            // Reload currency definitions (symbol, format, etc.); this preserves existing in-memory balances.
            //
            se.reloadCurrencyFormat();
            se.reloadStorageSettings();
            scheduleAutosave(se);
        }
        // Reload services v2.1
        if (bankService != null) {
            bankService.stop();
            bankService.start();
        }
        if (topCache != null) {
            topCache.invalidateAll();
        }
        if (discord != null) {
            discord.reload();
        }
    }

    private void migrateConfig() {
        org.bukkit.configuration.file.FileConfiguration cfg = getConfig();
        boolean changed = false;
        for (String obsolete : new String[] {"permissions", "loans", "update_check", "update_check_debug"}) {
            if (cfg.contains(obsolete)) {
                cfg.set(obsolete, null);
                changed = true;
            }
        }
        // Ensure config reflects current plugin version
        String currentVersion = getDescription().getVersion();
        if (!currentVersion.equals(cfg.getString("plugin_version"))) {
            cfg.set("plugin_version", currentVersion);
            changed = true;
        }
        if (changed) {
            saveConfig();
            getLogger().info("Removed obsolete configuration sections from config.yml (if present).");
            getLogger().info("Synchronized 'plugin_version' in config.yml to " + currentVersion);
        }
    }

    private void syncConfigFile() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
            reloadConfig();
            migrateConfig();
            getLogger().info("Recreated missing config.yml from plugin defaults.");
            return;
        }

        reloadConfig();
        InputStream in = getResource("config.yml");
        YamlConfiguration defaults = null;
        if (in != null) {
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        boolean changed = false;
        if (defaults != null) {
            YamlConfiguration fileCfg = YamlConfiguration.loadConfiguration(configFile);
            for (String path : defaults.getKeys(true)) {
                if (path == null || path.isEmpty()) continue;
                if (defaults.isConfigurationSection(path)) {
                    if (!fileCfg.isConfigurationSection(path)) {
                        fileCfg.createSection(path);
                        changed = true;
                    }
                    continue;
                }
                if (!fileCfg.contains(path)) {
                    fileCfg.set(path, defaults.get(path));
                    changed = true;
                }
            }
            if (changed) {
                try {
                    fileCfg.save(configFile);
                } catch (Exception ex) {
                    getLogger().warning("Failed to update config.yml with new defaults: " + ex.getMessage());
                }
                reloadConfig();
                getLogger().info("Added missing entries to config.yml while preserving existing values.");
            }
        }

        migrateConfig();
    }

    @Override
    public void onDisable() {
        com.paimc.vault.util.LogoPrinter.printDisable(this);
        // Unregister our Economy service
        if (economy != null) getServer().getServicesManager().unregister(Economy.class, economy);
        // Shutdown services v2.1
        if (bankService != null) {
            try { bankService.stop(); } catch (Throwable ignored) {}
            bankService = null;
        }
        if (topCache != null) {
            try { topCache.shutdown(); } catch (Throwable ignored) {}
            topCache = null;
        }
        // Cancel autosave
        if (autosaveTask != null) {
            try { autosaveTask.cancel(); } catch (Exception ignored) {}
            autosaveTask = null;
        }
        // Persist balances on shutdown
        if (economy instanceof SimpleEconomy) {
            try {
                ((SimpleEconomy) economy).saveAllNow();
            } catch (java.io.IOException ex) {
                getLogger().warning("Failed to save balances: " + ex.getMessage());
            }
            // Close SQL connection if used
            ((SimpleEconomy) economy).close();
        }
        // Shutdown transaction logger (flushes pending + persists serial)
        if (txLog != null) {
            try {
                txLog.shutdown();
            } catch (Throwable t) {
                getLogger().warning("Failed to shutdown transaction logger: " + t.getMessage());
            }
            txLog = null;
        }
        getLogger().info(messages.get("plugin.disabled"));
    }

    public VaultMenuService getVaultMenuService() {
        return vaultMenuService;
    }

    private void scheduleAutosave(SimpleEconomy provider) {
        if (autosaveTask != null) {
            try { autosaveTask.cancel(); } catch (Exception ignored) { }
            autosaveTask = null;
        }
        long seconds = getConfig().getLong("storage.autosave_seconds", 60L);
        if (seconds <= 0L) return;
        autosaveTask = getServer().getAsyncScheduler().runAtFixedRate(this, task -> {
            try { provider.save(); }
            catch (java.io.IOException ex) { getLogger().warning("Autosave failed: " + ex.getMessage()); }
        }, seconds, seconds, TimeUnit.SECONDS);
    }

}
