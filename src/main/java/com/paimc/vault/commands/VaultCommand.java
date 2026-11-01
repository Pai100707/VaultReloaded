package com.paimc.vault.commands;

import com.paimc.vault.VaultPlugin;
import com.paimc.vault.economy.BankService;
import com.paimc.vault.economy.OfflinePayQueueService;
import com.paimc.vault.economy.PhysicalNoteService;
import com.paimc.vault.economy.SimpleEconomy;
import com.paimc.vault.economy.TopCacheService;
import com.paimc.vault.i18n.Messages;
import com.paimc.vault.menu.HistoryMenuService;
import com.paimc.vault.menu.VaultMenuService;
import com.paimc.vault.transactions.TxRecord;
import com.paimc.vault.transactions.TxType;
import com.paimc.vault.util.PlayerResolver;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VaultCommand implements CommandExecutor {
    private final VaultPlugin plugin;
    private final Messages messages;
    private final VaultMenuService menuService;
    public volatile TopCacheService topCache;
    public volatile PhysicalNoteService noteService;
    public volatile OfflinePayQueueService offlinePay;
    public volatile BankService bankService;
    public volatile HistoryMenuService historyMenu;

    public VaultCommand(VaultPlugin plugin, Messages messages, VaultMenuService menuService) {
        this.plugin = plugin;
        this.messages = messages;
        this.menuService = menuService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            if (!p.isOp() && !p.hasPermission("vault.admin")) {
                sender.sendMessage(messages.chat("cmd.vault.no_permission"));
                return true;
            }
        }
        if (args.length == 0) {
            if (sender instanceof Player) {
                menuService.openMainMenu((Player) sender);
            } else {
                sender.sendMessage(usage());
            }
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("cancel".equals(sub)) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (menuService.cancelAdminEdit(player)) return true;
            return true;
        }
        if ("resetbalances".equals(sub) || "clearbalances".equals(sub)) {
            if (args.length < 2 || !"confirm".equalsIgnoreCase(args[1])) {
                sender.sendMessage(messages.formatChat("cmd.vault.reset_confirm", java.util.Collections.singletonMap("subcommand", sub)));
                return true;
            }
            if (!(plugin.getEconomyProvider() instanceof SimpleEconomy)) {
                sender.sendMessage(messages.chat("cmd.vault.reset_unavailable"));
                return true;
            }
            SimpleEconomy se = (SimpleEconomy) plugin.getEconomyProvider();
            for (String cid : se.getCurrencyIds()) {
                com.paimc.vault.economy.CurrencyData d = se.getCurrencyData(cid);
                d.balances.clear();
                d.worldBalances.clear();
            }
            se.clearPendingDatabaseWrites();
            com.paimc.vault.storage.Database db = se.getDatabase();
            if (db != null) {
                try {
                    db.clearAllBalances();
                } catch (java.sql.SQLException ex) {
                    sender.sendMessage(messages.formatChat("cmd.vault.reset_database_error", java.util.Collections.singletonMap("error", ex.getMessage())));
                    return true;
                }
            }
            try {
                se.saveAllNow();
            } catch (Exception ex) {
                sender.sendMessage(messages.formatChat("cmd.vault.reset_save_error", java.util.Collections.singletonMap("error", ex.getMessage())));
                return true;
            }
            if (plugin.getConfig().getBoolean("import.essentials.enabled", false)) {
                plugin.getConfig().set("import.essentials.enabled", false);
                plugin.saveConfig();
            }
            if (topCache != null) topCache.invalidateAll();
            sender.sendMessage(messages.chat("cmd.vault.reset_success"));
            return true;
        }
        if ("top".equals(sub)) {
            String cid = plugin.getEconomyProvider() instanceof SimpleEconomy ?
                    ((SimpleEconomy) plugin.getEconomyProvider()).getDefaultCurrencyId() : "default";
            int page = 1;
            if (args.length >= 2) {
                try { page = Integer.parseInt(args[1]); }
                catch (NumberFormatException ignored) {
                    if (plugin.getEconomyProvider() instanceof SimpleEconomy &&
                            ((SimpleEconomy) plugin.getEconomyProvider()).getCurrencyIds().contains(args[1].toLowerCase())) {
                        cid = args[1].toLowerCase();
                    }
                }
            }
            if (args.length >= 3) {
                if (plugin.getEconomyProvider() instanceof SimpleEconomy &&
                        ((SimpleEconomy) plugin.getEconomyProvider()).getCurrencyIds().contains(args[2].toLowerCase())) {
                    cid = args[2].toLowerCase();
                }
            }
            if (page < 1) page = 1;
            List<TopCacheService.TopEntry> entries;
            if (topCache != null) {
                entries = topCache.getTop(cid, 10000);
            } else if (plugin.getEconomyProvider() instanceof SimpleEconomy) {
                Map<UUID, Double> balances = ((SimpleEconomy) plugin.getEconomyProvider()).snapshotBalances(cid);
                List<Map.Entry<UUID, Double>> raw = new ArrayList<>(balances.entrySet());
                raw.sort(Comparator.<Map.Entry<UUID, Double>, Double>comparing(Map.Entry::getValue).reversed());
                entries = new ArrayList<>();
                int rank = 1;
                for (Map.Entry<UUID, Double> e : raw) {
                    UUID u = e.getKey();
                    OfflinePlayer p = Bukkit.getOfflinePlayer(u);
                    String n = p != null ? p.getName() : null;
                    if (n == null || n.trim().isEmpty()) n = u.toString().substring(0, 8);
                    entries.add(new TopCacheService.TopEntry(rank, u, n, e.getValue()));
                    rank++;
                }
            } else {
                sender.sendMessage(messages.chat("vaultop.top_unavailable"));
                return true;
            }
            int pageSize = 10;
            int total = entries.size();
            int totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
            if (page > totalPages) page = totalPages;
            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, total);
            Map<String, String> header = new HashMap<>();
            header.put("page", String.valueOf(page));
            header.put("pages", String.valueOf(totalPages));
            sender.sendMessage(messages.formatChat("vaultop.top_header", header));
            if (total == 0) {
                sender.sendMessage(messages.chat("vaultop.no_data"));
                return true;
            }
            SimpleEconomy se = plugin.getEconomyProvider() instanceof SimpleEconomy ?
                    (SimpleEconomy) plugin.getEconomyProvider() : null;
            for (int i = start; i < end; i++) {
                TopCacheService.TopEntry e = entries.get(i);
                String amount = se != null ? se.format(cid, e.balance) : plugin.getEconomyProvider().format(e.balance);
                sender.sendMessage(messages.formatChat("vaultop.top_line", Map.of(
                        "rank", String.valueOf(e.rank), "player", resolveTopName(e.uuid), "amount", amount)));
            }
            return true;
        }
        if ("withdraw".equals(sub) || "retirar".equals(sub)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(messages.chat("cmd.common.only_players"));
                return true;
            }
            if (!(plugin.getEconomyProvider() instanceof SimpleEconomy) || noteService == null) {
                sender.sendMessage(messages.chat("note.withdraw.disabled"));
                return true;
            }
            Player p = (Player) sender;
            if (args.length < 2) {
                p.sendMessage(messages.prefixed(messages.color("note.withdraw.usage")));
                return true;
            }
            double amount;
            try { amount = Double.parseDouble(args[1]); }
            catch (NumberFormatException ex) {
                p.sendMessage(messages.chat("cmd.common.invalid_amount"));
                return true;
            }
            if (!Double.isFinite(amount) || amount <= 0) {
                p.sendMessage(messages.chat("cmd.common.positive_only"));
                return true;
            }
            String cid = args.length >= 3 ? args[2].toLowerCase() :
                    ((SimpleEconomy) plugin.getEconomyProvider()).getDefaultCurrencyId();
            ItemStack note = noteService.withdrawNote(p, cid, amount, null);
            if (note == null) {
                p.sendMessage(messages.chat("cmd.common.insufficient_funds"));
                return true;
            }
            Map<Integer, ItemStack> leftover = p.getInventory().addItem(note);
            if (leftover != null && !leftover.isEmpty()) {
                for (ItemStack s : leftover.values()) p.getWorld().dropItemNaturally(p.getLocation(), s);
            }
            Map<String, String> ctx = new java.util.LinkedHashMap<>();
            ctx.put("amount", ((SimpleEconomy) plugin.getEconomyProvider()).format(cid, amount));
            ctx.put("currency", cid);
            p.sendMessage(messages.formatChat("note.withdraw.success", ctx));
            return true;
        }
        if ("history".equals(sub) || "historial".equals(sub)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(messages.chat("cmd.common.only_players"));
                return true;
            }
            Player p = (Player) sender;
            if (!(plugin.getEconomyProvider() instanceof SimpleEconomy) ||
                    ((SimpleEconomy) plugin.getEconomyProvider()).getTransactionLogService() == null) {
                p.sendMessage(messages.chat("history.unavailable"));
                return true;
            }
            SimpleEconomy se = (SimpleEconomy) plugin.getEconomyProvider();
            int page = 1;
            if (args.length >= 2) {
                try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
            }
            if (page < 1) page = 1;
            List<TxRecord> all = se.getTransactionLogService().recentForPlayer(p.getUniqueId(), 50);
            if (historyMenu != null) {
                historyMenu.openHistory(p, page, all);
            } else {
                int pageSize = 10;
                int total = all.size();
                int totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
                if (page > totalPages) page = totalPages;
                int start = (page - 1) * pageSize;
                int end = Math.min(start + pageSize, total);
                Map<String, String> ctx = new java.util.LinkedHashMap<>();
                ctx.put("page", String.valueOf(page));
                ctx.put("pages", String.valueOf(totalPages));
                p.sendMessage(messages.color("history.chat.header_top"));
                p.sendMessage(messages.colorize(messages.format("history.chat.header_title", ctx)));
                p.sendMessage(messages.color("history.chat.header_bottom"));
                if (total == 0) {
                    p.sendMessage(messages.color("history.chat.empty"));
                } else {
                    for (int i = start; i < end; i++) {
                        TxRecord r = all.get(i);
                        String when = formatTime(r.getInstantMs());
                        String tname = r.getTxType().name();
                        String dir;
                        if (p.getUniqueId().equals(r.getFromUuid())) dir = ChatColor.RED + "-";
                        else if (p.getUniqueId().equals(r.getToUuid())) dir = ChatColor.GREEN + "+";
                        else dir = " ";
                        String amt = se.format(r.getCurrencyId(), Math.abs(r.getAmount()));
                        String other = "—";
                        TxType tt = r.getTxType();
                        if (tt == TxType.PLAYER_PAY || tt == TxType.OFFLINE_PAY_CLAIMED || tt == TxType.OFFLINE_PAY_SENT) {
                            UUID o = p.getUniqueId().equals(r.getFromUuid()) ? r.getToUuid() : r.getFromUuid();
                            if (o != null) {
                                OfflinePlayer op = Bukkit.getOfflinePlayer(o);
                                other = op != null && op.getName() != null ? op.getName() : o.toString().substring(0, 8);
                            }
                        }
                        ctx.clear();
                        ctx.put("time", when);
                        ctx.put("dir", dir);
                        ctx.put("amount", amt);
                        ctx.put("type", tname);
                        ctx.put("serial", String.valueOf(r.getSerial()));
                        ctx.put("from", p.getUniqueId().equals(r.getFromUuid()) ? "yo" : other);
                        ctx.put("to", p.getUniqueId().equals(r.getToUuid()) ? "yo" : other);
                        p.sendMessage(messages.colorize(messages.format("history.chat.line_format", ctx)));
                        p.sendMessage(messages.colorize(messages.format("history.chat.line_meta", ctx)));
                    }
                }
                p.sendMessage(messages.color("history.chat.footer"));
            }
            return true;
        }
        if ("offlinepay".equals(sub)) {
            if (!(sender instanceof Player) && !sender.isOp()) {
                sender.sendMessage(messages.chat("cmd.vault.no_permission"));
                return true;
            }
            if (sender instanceof Player && !sender.hasPermission("vault.admin") && !sender.isOp()) {
                sender.sendMessage(messages.chat("cmd.vault.no_permission"));
                return true;
            }
            if (offlinePay == null) {
                sender.sendMessage(messages.chat("offlinepay.unavailable"));
                return true;
            }
            if (args.length >= 2 && "refund".equalsIgnoreCase(args[1])) {
                if (args.length < 3) {
                    sender.sendMessage(messages.prefixed(messages.color("offlinepay.refund.usage")));
                    return true;
                }
                long id;
                try { id = Long.parseLong(args[2]); }
                catch (NumberFormatException ex) {
                    sender.sendMessage(messages.chat("offlinepay.refund.invalid_id"));
                    return true;
                }
                boolean ok = offlinePay.refund(id);
                Map<String, String> ctx = new java.util.LinkedHashMap<>();
                ctx.put("id", String.valueOf(id));
                sender.sendMessage(messages.formatChat(ok ? "offlinepay.refund.success" : "offlinepay.refund.not_found", ctx));
                return true;
            }
            List<OfflinePayQueueService.QueuedPay> all = offlinePay.listAll();
            Map<String, String> ctx = new java.util.LinkedHashMap<>();
            ctx.put("count", String.valueOf(all.size()));
            sender.sendMessage(messages.formatChat("offlinepay.list.header", ctx));
            if (all.isEmpty()) {
                sender.sendMessage(messages.color("offlinepay.list.empty"));
            } else {
                for (OfflinePayQueueService.QueuedPay q : all) {
                    ctx.clear();
                    ctx.put("id", String.valueOf(q.id));
                    ctx.put("from_name", q.fromName);
                    ctx.put("to_name", q.toName);
                    ctx.put("amount", String.valueOf(q.amount));
                    ctx.put("time_ago", formatTime(q.createdAtMs));
                    sender.sendMessage(messages.colorize(messages.format("offlinepay.list.line", ctx)));
                }
            }
            return true;
        }
        if ("reload".equals(sub)) {
            if (sender instanceof Player && !sender.isOp()) {
                sender.sendMessage(messages.chat("cmd.vault.no_permission"));
                return true;
            }
            plugin.reloadPluginState();
            String lang = plugin.getConfig().getString("language", "en");
            sender.sendMessage(messages.formatChat("plugin.reloaded", java.util.Collections.singletonMap("lang", lang)));
            return true;
        }
        if ("bank".equals(sub)) {
            if (bankService == null) {
                sender.sendMessage(messages.chat("bank.unavailable"));
                return true;
            }
            if (!(plugin.getEconomyProvider() instanceof SimpleEconomy)) {
                sender.sendMessage(messages.chat("bank.unavailable"));
                return true;
            }
            SimpleEconomy se = (SimpleEconomy) plugin.getEconomyProvider();
            String cid = se.getDefaultCurrencyId();
            String action = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "balance";
            if ("balance".equals(action) || "bal".equals(action)) {
                if (args.length >= 3) {
                    String target = args[2];
                    OfflinePlayer op = PlayerResolver.resolveByNameWithOfflineFallback(plugin, target);
                    if (op == null || (op.getName() == null && !op.hasPlayedBefore())) {
                        Map<String, String> ctx = new LinkedHashMap<>();
                        ctx.put("player", target);
                        sender.sendMessage(messages.formatChat("bank.not_found", ctx));
                        return true;
                    }
                    double wallet = se.getBalance(cid, op);
                    double bank = bankService.getBankBalance(op.getUniqueId());
                    Map<String, String> ctx = new LinkedHashMap<>();
                    ctx.put("player", op.getName() == null ? op.getUniqueId().toString() : op.getName());
                    ctx.put("wallet", se.format(cid, wallet));
                    ctx.put("bank", se.format(cid, bank));
                    ctx.put("total", se.format(cid, wallet + bank));
                    sender.sendMessage(messages.colorize(messages.format("bank.balance.other_header", ctx)));
                    sender.sendMessage(messages.colorize(messages.format("bank.balance.other_wallet", ctx)));
                    sender.sendMessage(messages.colorize(messages.format("bank.balance.other_bank", ctx)));
                    sender.sendMessage(messages.colorize(messages.format("bank.balance.other_total", ctx)));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(messages.chat("bank.only_players"));
                    return true;
                }
                Player p = (Player) sender;
                double wallet = se.getBalance(cid, p);
                double bank = bankService.getBankBalance(p.getUniqueId());
                double total = wallet + bank;
                boolean interestOn = bankService.isInterestEnabled();
                double irateRaw = plugin.getConfig().getDouble("bank.interest.percent_per_period", 0.5);
                double irate = interestOn ? irateRaw : 0.0;
                long imin = Math.max(1L, plugin.getConfig().getLong("bank.interest.every_minutes", 60L));
                boolean taxOn = bankService.isTaxEnabled();
                double trateRaw = plugin.getConfig().getDouble("bank.tax.percent_per_period", 0.0);
                double trate = taxOn ? trateRaw : 0.0;
                long tmin = Math.max(1L, plugin.getConfig().getLong("bank.tax.every_minutes", 180L));
                double tthresh = plugin.getConfig().getDouble("bank.tax.threshold", 1000000.0);
                Map<String, String> ctx = new LinkedHashMap<>();
                ctx.put("wallet", se.format(cid, wallet));
                ctx.put("bank", se.format(cid, bank));
                ctx.put("total", se.format(cid, total));
                double interestNext = interestOn ? bank * irate / 100.0 : 0.0;
                double taxNext = taxOn ? Math.max(0, Math.max(0, bank - tthresh) * trate / 100.0) : 0.0;
                String interestNextFmt;
                if (!interestOn || irate <= 0) {
                    String t = messages.getOptional("bank.balance.interest_disabled");
                    interestNextFmt = messages.colorize(t == null || t.isEmpty() ? "&aDesactivado" : t);
                } else {
                    interestNextFmt = se.format(cid, interestNext);
                }
                ctx.put("interest_next", interestNextFmt);
                ctx.put("interest_pct", String.format("%.2f", irateRaw));
                ctx.put("interest_min", String.valueOf(imin));
                ctx.put("tax_pct", String.format("%.2f", trateRaw));
                ctx.put("tax_min", String.valueOf(tmin));
                ctx.put("tax_threshold", se.format(cid, tthresh));
                ctx.put("tax_next", se.format(cid, taxNext));
                sender.sendMessage(messages.colorize(messages.format("bank.balance.self_header", ctx)));
                sender.sendMessage(messages.colorize(messages.format("bank.balance.self_wallet", ctx)));
                sender.sendMessage(messages.colorize(messages.format("bank.balance.self_bank", ctx)));
                sender.sendMessage(messages.colorize(messages.format("bank.balance.self_total", ctx)));
                if (interestOn && irate > 0)
                    sender.sendMessage(messages.colorize(messages.format("bank.balance.self_interest", ctx)));
                if (taxOn && trate > 0) {
                    if (bank <= tthresh)
                        sender.sendMessage(messages.color(messages.getOptional("bank.balance.self_no_tax")));
                    else
                        sender.sendMessage(messages.colorize(messages.format("bank.balance.self_tax", ctx)));
                }
                return true;
            }
            if ("deposit".equals(action) || "dep".equals(action)) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(messages.chat("bank.only_players"));
                    return true;
                }
                Player p = (Player) sender;
                if (args.length < 3) {
                    p.sendMessage(messages.chat("bank.deposit.usage"));
                    return true;
                }
                Double amt;
                try {
                    amt = Double.parseDouble(args[2].replace(",", "."));
                    if (!Double.isFinite(amt)) amt = null;
                } catch (NumberFormatException ignored) { amt = null; }
                if (amt == null) {
                    p.sendMessage(messages.chat("bank.invalid_amount"));
                    return true;
                }
                if (amt <= 0) {
                    p.sendMessage(messages.chat("bank.positive_only"));
                    return true;
                }
                double wallet = se.getBalance(cid, p);
                if (wallet + 0.0000001 < amt) {
                    p.sendMessage(messages.chat("bank.insufficient_funds"));
                    return true;
                }
                boolean ok = bankService.depositBank(p.getUniqueId(), amt);
                Map<String, String> ctx = new LinkedHashMap<>();
                ctx.put("amount", se.format(cid, amt));
                ctx.put("bank_new", se.format(cid, bankService.getBankBalance(p.getUniqueId())));
                if (!ok) {
                    ctx.put("error", "deposit rejected");
                    p.sendMessage(messages.formatChat("bank.deposit.error", ctx));
                    return true;
                }
                p.sendMessage(messages.formatChat("bank.deposit.success", ctx));
                return true;
            }
            if ("withdraw".equals(action) || "with".equals(action) || "wd".equals(action)) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(messages.chat("bank.only_players"));
                    return true;
                }
                Player p = (Player) sender;
                if (args.length < 3) {
                    p.sendMessage(messages.chat("bank.withdraw.usage"));
                    return true;
                }
                Double amt;
                try {
                    amt = Double.parseDouble(args[2].replace(",", "."));
                    if (!Double.isFinite(amt)) amt = null;
                } catch (NumberFormatException ignored) { amt = null; }
                if (amt == null) {
                    p.sendMessage(messages.chat("bank.invalid_amount"));
                    return true;
                }
                if (amt <= 0) {
                    p.sendMessage(messages.chat("bank.positive_only"));
                    return true;
                }
                double bank = bankService.getBankBalance(p.getUniqueId());
                if (bank + 0.0000001 < amt) {
                    p.sendMessage(messages.chat("bank.insufficient_bank"));
                    return true;
                }
                boolean ok = bankService.withdrawBank(p.getUniqueId(), amt);
                Map<String, String> ctx = new LinkedHashMap<>();
                ctx.put("amount", se.format(cid, amt));
                ctx.put("wallet_new", se.format(cid, se.getBalance(cid, p)));
                if (!ok) {
                    ctx.put("error", "withdraw rejected");
                    p.sendMessage(messages.formatChat("bank.withdraw.error", ctx));
                    return true;
                }
                p.sendMessage(messages.formatChat("bank.withdraw.success", ctx));
                return true;
            }
            if ("top".equals(action)) {
                int page = 1;
                if (args.length >= 3) {
                    try {
                        page = Integer.parseInt(args[2]);
                        if (page < 1) {
                            sender.sendMessage(messages.chat("bank.invalid_page"));
                            return true;
                        }
                    } catch (NumberFormatException ignored) {
                        sender.sendMessage(messages.chat("bank.invalid_page"));
                        return true;
                    }
                }
                java.util.concurrent.ConcurrentMap<UUID, Double> bb;
                try {
                    java.lang.reflect.Field f = bankService.getClass().getDeclaredField("bankBalances");
                    f.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    java.util.concurrent.ConcurrentMap<UUID, Double> r = (java.util.concurrent.ConcurrentMap<UUID, Double>) f.get(bankService);
                    bb = r;
                } catch (Throwable t) {
                    bb = new java.util.concurrent.ConcurrentHashMap<>();
                }
                List<Map.Entry<UUID, Double>> entries = new ArrayList<>(bb.entrySet());
                entries.sort(java.util.Map.Entry.comparingByValue(Comparator.reverseOrder()));
                int perPage = 10;
                int total = entries.size();
                int pages = Math.max(1, (total + perPage - 1) / perPage);
                if (page > pages) {
                    sender.sendMessage(messages.chat("bank.invalid_page"));
                    return true;
                }
                Map<String, String> ctx = new LinkedHashMap<>();
                ctx.put("page", String.valueOf(page));
                ctx.put("pages", String.valueOf(pages));
                sender.sendMessage(messages.colorize(messages.format("bank.top.header", ctx)));
                if (entries.isEmpty()) {
                    sender.sendMessage(messages.color(messages.getOptional("bank.top.empty")));
                } else {
                    int start = (page - 1) * perPage;
                    int end = Math.min(start + perPage, total);
                    int pos = start + 1;
                    for (int i = start; i < end; i++) {
                        Map.Entry<UUID, Double> e = entries.get(i);
                        OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
                        double bank = e.getValue();
                        double wallet = se.getBalance(se.getDefaultCurrencyId(), op);
                        ctx.clear();
                        ctx.put("pos", String.valueOf(pos++));
                        ctx.put("player", op.getName() == null ? e.getKey().toString().substring(0, 8) : op.getName());
                        ctx.put("bank", se.format(se.getDefaultCurrencyId(), bank));
                        ctx.put("total", se.format(se.getDefaultCurrencyId(), bank + wallet));
                        sender.sendMessage(messages.colorize(messages.format("bank.top.line", ctx)));
                    }
                    if (page < pages)
                        sender.sendMessage(messages.color(messages.getOptional("bank.top.footer")));
                }
                return true;
            }
            // Menu / open GUI for bank
            if ("menu".equals(action) || "gui".equals(action) || "open".equals(action)) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(messages.chat("bank.only_players"));
                    return true;
                }
                // Will be handled in VaultMenuService; route through there.
                menuService.openBankMenu((Player) sender);
                return true;
            }
            // No matched sub-action
            String usageRaw = messages.getOptional("bank.usage");
            if (usageRaw == null || usageRaw.isEmpty()) {
                sender.sendMessage(messages.chat("bank.usage"));
                return true;
            }
            for (String line : usageRaw.split("\\r?\\n")) {
                sender.sendMessage(messages.prefixed(messages.colorize(line)));
            }
            return true;
        }
        sender.sendMessage(usage());
        return true;
    }

    private String resolveTopName(UUID uuid) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline != null ? offline.getName() : null;
        return name == null || name.isBlank() ? messages.get("top.unknown_player") : name;
    }

    private String usage() {
        String raw = messages.getOptional("cmd.vault.usage");
        String prefix = messages.prefix();
        String prefixWithSpace = prefix.isEmpty() ? "" : prefix + " ";
        if (raw == null || raw.isBlank()) return prefixWithSpace + messages.color("cmd.common.usage_prefix") + " /vault";
        StringBuilder output = new StringBuilder();
        for (String line : raw.split("\\r?\\n")) {
            if (output.length() > 0) output.append('\n');
            output.append(prefixWithSpace).append(messages.colorize(line));
        }
        return output.toString();
    }

    private static String formatTime(long ms) {
        long diff = Math.max(0L, System.currentTimeMillis() - ms) / 1000L;
        if (diff < 60) return diff + "s";
        if (diff < 3600) return (diff / 60) + "m";
        if (diff < 86400) return (diff / 3600) + "h";
        return (diff / 86400) + "d";
    }
}
