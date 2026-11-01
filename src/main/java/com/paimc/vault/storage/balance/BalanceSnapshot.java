package com.paimc.vault.storage.balance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class BalanceSnapshot {
    private final Map<String, Map<UUID, Double>> balances = new LinkedHashMap<>();
    private final Map<String, Map<String, Map<UUID, Double>>> worldBalances = new LinkedHashMap<>();

    public Map<String, Map<UUID, Double>> balances() { return balances; }
    public Map<String, Map<String, Map<UUID, Double>>> worldBalances() { return worldBalances; }

    public void put(String currency, UUID player, String world, double balance) {
        if (world == null || world.isBlank()) {
            balances.computeIfAbsent(currency, ignored -> new LinkedHashMap<>()).put(player, balance);
        } else {
            worldBalances.computeIfAbsent(currency, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(world.toLowerCase(java.util.Locale.ROOT), ignored -> new LinkedHashMap<>())
                    .put(player, balance);
        }
    }
}
