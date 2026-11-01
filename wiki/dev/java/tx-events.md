---
title: Transaction Logs & Events
description: Transaction types, transaction records and event integration in Vault 2.1.2.
---

# Transaction Logs & Events

Vault records balance operations in transaction history and can expose transaction events to integrations. This system is separate from the selectable Balance storage backend.

## Transaction types

The current `TxType` values include:

| Type | Purpose |
|---|---|
| `PLAYER_PAY` | Player-to-player payment |
| `ADMIN_SET` | Administrator sets a balance |
| `ADMIN_ADD` | Administrator adds balance |
| `ADMIN_REMOVE` | Administrator removes balance |
| `ADMIN_RESET` | Administrator resets a balance |
| `DEPOSIT` / `WITHDRAW` | Balance deposit or withdrawal |
| `NOTE_WITHDRAW` / `NOTE_REDEEM` | Physical note issue or redemption |
| `OFFLINE_PAY_SENT` / `OFFLINE_PAY_CLAIMED` / `OFFLINE_PAY_REFUNDED` | Offline payment lifecycle |
| `BANK_DEPOSIT` / `BANK_WITHDRAW` | Bank deposit or withdrawal |
| `INTEREST` / `TAX` | Bank interest or tax |
| `TEAM_DEPOSIT` / `TEAM_WITHDRAW` / `TEAM_DISBAND_REFUND` | Team balance operations |
| `CHARGE_PAID` | Charge request payment |

## Record a transaction

Use the transaction service exposed by the plugin rather than writing directly to its storage files or tables. Keep transaction persistence independent of `storage.balances.type`; that setting controls only balance snapshots.

## Folia compatibility

When handling transaction events, schedule player/entity interactions with the appropriate entity scheduler. Do not access player state from an arbitrary asynchronous database callback.

## Data locations

- Daily file logs are stored under `plugins/Vault/logs/`.
- SQL transaction data uses the existing transaction table when the SQL transaction backend is enabled.
- Balance data is stored by the selected backend in `storage.balances.type`.
