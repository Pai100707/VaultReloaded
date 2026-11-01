package com.paimc.vault.storage.balance;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;
import java.util.UUID;

public final class YamlBalanceStorage implements BalanceStorage {
    private final File file;
    public YamlBalanceStorage(File dataFolder) { this.file = new File(dataFolder, "balances.yml"); }

    @Override public synchronized BalanceSnapshot load() {
        BalanceSnapshot result = new BalanceSnapshot();
        if (!file.isFile()) return result;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection currencies = cfg.getConfigurationSection("currencies");
        if (currencies != null) {
            for (String currency : currencies.getKeys(false)) {
                readSection(result, currency, currencies.getConfigurationSection(currency + ".balances"), "");
                ConfigurationSection worlds = currencies.getConfigurationSection(currency + ".world_balances");
                if (worlds != null) for (String world : worlds.getKeys(false)) {
                    readSection(result, currency, worlds.getConfigurationSection(world), world);
                }
            }
        }
        readSection(result, "default", cfg.getConfigurationSection("balances"), "");
        ConfigurationSection worlds = cfg.getConfigurationSection("world_balances");
        if (worlds != null) for (String world : worlds.getKeys(false)) {
            readSection(result, "default", worlds.getConfigurationSection(world), world);
        }
        return result;
    }

    private void readSection(BalanceSnapshot out, String currency, ConfigurationSection section, String world) {
        if (section == null) return;
        for (String key : section.getKeys(false)) try {
            UUID uuid = UUID.fromString(key);
            double value = section.getDouble(key, 0.0);
            if (Double.isFinite(value)) out.put(currency, uuid, world, value);
        } catch (IllegalArgumentException ignored) { }
    }

    @Override public synchronized void saveChanges(java.util.Map<BalanceKey, Double> changes) throws Exception {
        // YAML is a snapshot format; the economy passes a consistent in-memory snapshot on every flush.
        throw new UnsupportedOperationException("Use saveAll for YAML snapshots");
    }

    @Override public synchronized void saveAll(BalanceSnapshot snapshot) throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        Map<UUID, Double> flat = snapshot.balances().get("default");
        if (flat != null) flat.forEach((uuid, amount) -> cfg.set("balances." + uuid, amount));
        Map<String, java.util.Map<UUID, Double>> defaultWorlds = snapshot.worldBalances().get("default");
        if (defaultWorlds != null) defaultWorlds.forEach((world, entries) ->
                entries.forEach((uuid, amount) -> cfg.set("world_balances." + world + "." + uuid, amount)));
        snapshot.balances().forEach((currency, entries) -> entries.forEach((uuid, amount) ->
                cfg.set("currencies." + currency + ".balances." + uuid, amount)));
        snapshot.worldBalances().forEach((currency, worlds) -> worlds.forEach((world, entries) ->
                entries.forEach((uuid, amount) -> cfg.set("currencies." + currency + ".world_balances." + world + "." + uuid, amount))));
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        cfg.save(file);
    }
    @Override public String type() { return "YAML"; }
}
