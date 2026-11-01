package com.paimc.vault.storage.balance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public final class JsonBalanceStorage implements BalanceStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final File file;
    public JsonBalanceStorage(File dataFolder) {
        this.file = new File(dataFolder, "balances.json");
    }

    @Override public synchronized BalanceSnapshot load() throws Exception {
        BalanceSnapshot out = new BalanceSnapshot();
        if (!file.isFile()) return out;
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject currencies = root.has("currencies") && root.get("currencies").isJsonObject()
                    ? root.getAsJsonObject("currencies") : new JsonObject();
            for (Map.Entry<String, JsonElement> currencyEntry : currencies.entrySet()) {
                if (!currencyEntry.getValue().isJsonObject()) continue;
                JsonObject currency = currencyEntry.getValue().getAsJsonObject();
                readBalances(out, currencyEntry.getKey(), currency.getAsJsonObject("balances"), "");
                if (currency.has("world_balances") && currency.get("world_balances").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> world : currency.getAsJsonObject("world_balances").entrySet()) {
                        if (world.getValue().isJsonObject()) readBalances(out, currencyEntry.getKey(), world.getValue().getAsJsonObject(), world.getKey());
                    }
                }
            }
            return out;
        }
    }

    private void readBalances(BalanceSnapshot out, String cid, JsonObject values, String world) {
        if (values == null) return;
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) try {
            double amount = entry.getValue().getAsDouble();
            if (Double.isFinite(amount)) out.put(cid, UUID.fromString(entry.getKey()), world, amount);
        } catch (RuntimeException ignored) { }
    }

    @Override public synchronized void saveChanges(Map<BalanceKey, Double> changes) {
        throw new UnsupportedOperationException("Use saveAll for JSON snapshots");
    }
    @Override public synchronized void saveAll(BalanceSnapshot snapshot) throws Exception {
        JsonObject root = new JsonObject();
        JsonObject currencies = new JsonObject();
        for (Map.Entry<String, Map<UUID, Double>> entry : snapshot.balances().entrySet()) {
            JsonObject currency = currencies.has(entry.getKey()) ? currencies.getAsJsonObject(entry.getKey()) : new JsonObject();
            JsonObject balances = new JsonObject();
            entry.getValue().forEach((uuid, amount) -> balances.addProperty(uuid.toString(), amount));
            currency.add("balances", balances);
            currencies.add(entry.getKey(), currency);
        }
        for (Map.Entry<String, Map<String, Map<UUID, Double>>> entry : snapshot.worldBalances().entrySet()) {
            JsonObject currency = currencies.has(entry.getKey()) ? currencies.getAsJsonObject(entry.getKey()) : new JsonObject();
            JsonObject worlds = new JsonObject();
            entry.getValue().forEach((world, balances) -> {
                JsonObject values = new JsonObject();
                balances.forEach((uuid, amount) -> values.addProperty(uuid.toString(), amount));
                worlds.add(world, values);
            });
            currency.add("world_balances", worlds);
            currencies.add(entry.getKey(), currency);
        }
        root.add("currencies", currencies);
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        File temp = new File(parent, file.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(temp, StandardCharsets.UTF_8)) { GSON.toJson(root, writer); }
        try {
            java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
    @Override public String type() { return "JSON"; }
}
