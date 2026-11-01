package com.paimc.vault.storage.balance;

import java.util.Objects;
import java.util.UUID;

public final class BalanceKey {
    private final String currencyId;
    private final UUID playerId;
    private final String worldName;

    public BalanceKey(String currencyId, UUID playerId, String worldName) {
        this.currencyId = Objects.requireNonNull(currencyId, "currencyId");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.worldName = worldName == null ? "" : worldName.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public String currencyId() { return currencyId; }
    public UUID playerId() { return playerId; }
    public String worldName() { return worldName; }
    public boolean isGlobal() { return worldName.isEmpty(); }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BalanceKey key)) return false;
        return currencyId.equals(key.currencyId) && playerId.equals(key.playerId) && worldName.equals(key.worldName);
    }
    @Override public int hashCode() { return Objects.hash(currencyId, playerId, worldName); }
}
