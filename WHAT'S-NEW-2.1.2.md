# What's New — Vault 2.1.2

- Migrated the project build from Maven to Gradle Kotlin DSL and added Gradle launcher scripts.
- Renamed the plugin's internal package namespace to `com.paimc.vault`.
- Removed the player loan feature, its command, menu, settings and language entries.
- Removed the old Modrinth updater, update command and update configuration.
- Added configurable inventory layouts under `gui_layout/`.
- Added Paper Dialog API support for the main Vault and settings menus, selectable from their GUI layout files.
- Added Folia scheduler integration and declared Folia support in `plugin.yml`.
- Added selectable balance storage: YAML, JSON, H2, SQLite, MySQL, MariaDB and PostgreSQL.
- Kept the existing transaction log and charge-request persistence separate from balance storage.
- Moved the plugin version to 2.1.2 and added `Pai100707` as the author.
