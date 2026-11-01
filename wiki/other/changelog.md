---
title: Changelog v2.1.2
description: Changes in Vault 2.1.2.
---

# Vault Changelog

## Version 2.1.2

- Migrated the build from Maven to Gradle Kotlin DSL and added Gradle launcher scripts.
- Renamed the plugin package namespace from `com.example.vault` to `com.paimc.vault`.
- Removed the Loans feature, related commands, menu, configuration and message entries.
- Removed the legacy update checker, update command and update configuration.
- Added per-menu YAML layouts under `plugins/Vault/gui_layout/`.
- Added Paper Dialog API support for the main Vault and settings menus, configurable per menu.
- Added Folia scheduler support and the `folia-supported: true` plugin descriptor flag.
- Added independent Balance storage backends: YAML, JSON, H2, SQLite, MySQL, MariaDB and PostgreSQL.
- Kept transaction-log and charge-request persistence separate from the selectable Balance backend.
- Updated the plugin version to `2.1.2` and added `Pai100707` as author.

See [What's New](../../WHAT'S-NEW-2.1.2.md) for the release summary.
