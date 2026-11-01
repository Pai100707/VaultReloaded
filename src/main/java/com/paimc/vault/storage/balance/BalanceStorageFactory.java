package com.paimc.vault.storage.balance;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Locale;

public final class BalanceStorageFactory {
    private BalanceStorageFactory() { }

    public static BalanceStorage create(Plugin plugin) throws Exception {
        FileConfiguration config = plugin.getConfig();
        String type = config.getString("storage.balances.type", "AUTO").trim().toUpperCase(Locale.ROOT);
        if (type.equals("AUTO")) type = config.getBoolean("storage.use_mysql", false) ? "MYSQL" : "YAML";
        switch (type) {
            case "YML": case "YAML": return new YamlBalanceStorage(plugin.getDataFolder());
            case "JSON": return new JsonBalanceStorage(plugin.getDataFolder());
            case "H2": case "SQLITE": case "MYSQL": case "MARIADB": case "POSTGRESQL": {
                String base = "storage.balances.sql.";
                String url = config.getString(base + "url", "").trim();
                if (url.isEmpty()) {
                    switch (type) {
                        case "H2": url = "jdbc:h2:file:" + new File(plugin.getDataFolder(), "balances").getAbsolutePath(); break;
                        case "SQLITE": url = "jdbc:sqlite:" + new File(plugin.getDataFolder(), "balances.db").getAbsolutePath(); break;
                        case "MYSQL": case "MARIADB": {
                            String host = config.getString("storage.mysql.host", "localhost");
                            int port = config.getInt("storage.mysql.port", 3306);
                            String database = config.getString("storage.mysql.database", "vault");
                            String params = config.getString(base + "params", "useSSL=false&serverTimezone=UTC");
                            String scheme = type.equals("MARIADB") ? "jdbc:mariadb://" : "jdbc:mysql://";
                            url = scheme + host + ":" + port + "/" + database + (params.isEmpty() ? "" : "?" + params);
                            break;
                        }
                        case "POSTGRESQL": {
                            String host = config.getString(base + "host", "localhost");
                            int port = config.getInt(base + "port", 5432);
                            String database = config.getString(base + "database", "vault");
                            url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
                            break;
                        }
                    }
                }
                String user = config.getString(base + "username", "");
                String password = config.getString(base + "password", "");
                if (type.equals("MYSQL") || type.equals("MARIADB")) {
                    if (user == null || user.isBlank()) {
                        user = config.getString("storage.mysql.user", config.getString("storage.mysql.username", ""));
                    }
                    if (password == null || password.isEmpty()) password = config.getString("storage.mysql.password", "");
                } else if (type.equals("H2") && (user == null || user.isBlank())) {
                    user = "sa";
                }
                return new JdbcBalanceStorage(type, url, user, password);
            }
            default: throw new IllegalArgumentException("Unknown storage.balances.type: " + type);
        }
    }
}
