package com.paimc.vault.storage.balance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

public final class JdbcBalanceStorage implements BalanceStorage {
    private static final String TABLE = "vault_balance_entries";
    private final String type;
    private final String url;
    private final String username;
    private final String password;
    private Connection connection;

    public JdbcBalanceStorage(String type, String url, String username, String password) throws Exception {
        this.type = type.toUpperCase(java.util.Locale.ROOT);
        this.url = url;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        switch (this.type) {
            case "H2": Class.forName("org.h2.Driver"); break;
            case "SQLITE": Class.forName("org.sqlite.JDBC"); break;
            case "MYSQL": Class.forName("com.mysql.cj.jdbc.Driver"); break;
            case "MARIADB": Class.forName("org.mariadb.jdbc.Driver"); break;
            case "POSTGRESQL": Class.forName("org.postgresql.Driver"); break;
            default: throw new IllegalArgumentException("Unsupported JDBC balance storage type: " + type);
        }
        this.connection = DriverManager.getConnection(url, this.username, this.password);
        ensureSchema();
    }

    private void ensureSchema() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "currency_id VARCHAR(64) NOT NULL, player_uuid VARCHAR(36) NOT NULL, "
                + "world_name VARCHAR(128) NOT NULL, balance DOUBLE PRECISION NOT NULL, "
                + "PRIMARY KEY (currency_id, player_uuid, world_name))";
        try (Statement statement = connection.createStatement()) { statement.executeUpdate(sql); }
    }

    @Override public synchronized BalanceSnapshot load() throws SQLException {
        BalanceSnapshot snapshot = new BalanceSnapshot();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT currency_id, player_uuid, world_name, balance FROM " + TABLE);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                try {
                    double value = result.getDouble("balance");
                    if (Double.isFinite(value)) snapshot.put(result.getString("currency_id"),
                            UUID.fromString(result.getString("player_uuid")), result.getString("world_name"), value);
                } catch (IllegalArgumentException ignored) { }
            }
        }
        // Migrate old MySQL data if this is the first run on the generic balance table.
        if (snapshot.balances().isEmpty() && snapshot.worldBalances().isEmpty() && (type.equals("MYSQL") || type.equals("MARIADB"))) {
            loadLegacyMySql(snapshot);
            if (!snapshot.balances().isEmpty() || !snapshot.worldBalances().isEmpty()) {
                saveAll(snapshot);
            }
        }
        return snapshot;
    }

    private void loadLegacyMySql(BalanceSnapshot snapshot) {
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT uuid, balance FROM vault_balances")) {
            while (rs.next()) try { snapshot.put("default", UUID.fromString(rs.getString(1)), "", rs.getDouble(2)); }
            catch (IllegalArgumentException ignored) { }
        } catch (SQLException ignored) { }
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT world_name, uuid, balance FROM vault_world_balances")) {
            while (rs.next()) try { snapshot.put("default", UUID.fromString(rs.getString(2)), rs.getString(1), rs.getDouble(3)); }
            catch (IllegalArgumentException ignored) { }
        } catch (SQLException ignored) { }
    }

    @Override public synchronized void saveChanges(Map<BalanceKey, Double> changes) throws SQLException {
        if (changes == null || changes.isEmpty()) return;
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + TABLE
                + " SET balance=? WHERE currency_id=? AND player_uuid=? AND world_name=?");
             PreparedStatement insert = connection.prepareStatement("INSERT INTO " + TABLE
                + " (balance,currency_id,player_uuid,world_name) VALUES (?,?,?,?)")) {
            for (Map.Entry<BalanceKey, Double> entry : changes.entrySet()) {
                BalanceKey key = entry.getKey();
                double value = entry.getValue() == null || !Double.isFinite(entry.getValue()) ? 0.0 : entry.getValue();
                update.setDouble(1, value);
                update.setString(2, key.currencyId());
                update.setString(3, key.playerId().toString());
                update.setString(4, key.worldName());
                int affected = update.executeUpdate();
                if (affected == 0) {
                    insert.setDouble(1, value);
                    insert.setString(2, key.currencyId());
                    insert.setString(3, key.playerId().toString());
                    insert.setString(4, key.worldName());
                    try { insert.executeUpdate(); }
                    catch (SQLException insertRace) {
                        update.setDouble(1, value);
                        update.setString(2, key.currencyId());
                        update.setString(3, key.playerId().toString());
                        update.setString(4, key.worldName());
                        // A zero update count can mean the row already has the same value
                        // (not that the row is missing), notably with MySQL Connector/J.
                        // The INSERT failed because of a concurrent duplicate key, so retry UPDATE.
                        update.executeUpdate();
                    }
                }
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally { connection.setAutoCommit(oldAutoCommit); }
    }

    @Override public synchronized void saveAll(BalanceSnapshot snapshot) throws SQLException {
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement clear = connection.createStatement()) {
            clear.executeUpdate("DELETE FROM " + TABLE);
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + TABLE
                    + " (currency_id,player_uuid,world_name,balance) VALUES (?,?,?,?)")) {
                snapshot.balances().forEach((currency, entries) -> entries.forEach((uuid, amount) -> {
                    try { insert.setString(1, currency); insert.setString(2, uuid.toString()); insert.setString(3, ""); insert.setDouble(4, amount); insert.addBatch(); }
                    catch (SQLException ex) { throw new RuntimeException(ex); }
                }));
                snapshot.worldBalances().forEach((currency, worlds) -> worlds.forEach((world, entries) -> entries.forEach((uuid, amount) -> {
                    try { insert.setString(1, currency); insert.setString(2, uuid.toString()); insert.setString(3, world); insert.setDouble(4, amount); insert.addBatch(); }
                    catch (SQLException ex) { throw new RuntimeException(ex); }
                })));
                insert.executeBatch();
            }
            connection.commit();
        } catch (RuntimeException | SQLException ex) {
            connection.rollback();
            if (ex instanceof SQLException sql) throw sql;
            throw new SQLException("Could not serialize balance snapshot", ex);
        } finally { connection.setAutoCommit(oldAutoCommit); }
    }

    @Override public String type() { return type; }
    @Override public synchronized void close() throws SQLException {
        if (connection != null && !connection.isClosed()) connection.close();
    }
}
