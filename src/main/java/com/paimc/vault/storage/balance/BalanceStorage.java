package com.paimc.vault.storage.balance;

import java.util.Map;

public interface BalanceStorage extends AutoCloseable {
    BalanceSnapshot load() throws Exception;
    void saveChanges(Map<BalanceKey, Double> changes) throws Exception;
    void saveAll(BalanceSnapshot snapshot) throws Exception;
    String type();
    @Override default void close() throws Exception { }
}
