# Vault 2.1.2

Vault is a Bukkit economy provider compatible with the Vault API. It exposes the economy service to other plugins and provides player payment menus, balances, optional banking, transaction history, physical notes, multi-currency support, PlaceholderAPI placeholders, offline payment requests and Essentials balance import.

## Requirements

- Paper 1.21.7 or newer (built against Paper 1.21.11)
- Java 21 or newer
- Folia is supported through its region/entity/async schedulers

## Build

The project uses Gradle Kotlin DSL. Build with the included launcher:

```bash
./gradlew clean build
```

Windows:

```bat
gradlew.bat clean build
```

The ZIP includes a small `gradlew` bootstrap launcher and the pinned Gradle distribution URL. To materialize the standard Gradle Wrapper scripts and `gradle-wrapper.jar`, run the Wrapper task twice (the first run downloads Gradle through the bootstrap launcher):

```bash
./gradlew wrapper --gradle-version 9.8.2 --distribution-type bin
./gradlew wrapper
```

Windows:

```bat
gradlew.bat wrapper --gradle-version 9.8.2 --distribution-type bin
gradlew.bat wrapper
```

If Gradle is already installed globally, the equivalent first command is `gradle wrapper --gradle-version 9.8.2 --distribution-type bin`. The generated plugin jar is in `build/libs/`.

## Balance storage

Balance persistence is selectable independently from the legacy SQL backend used for transaction logs and charge requests. Configure `storage.balances.type` in `config.yml`:

| Type | Storage |
|---|---|
| `AUTO` | Uses MySQL when `storage.use_mysql: true`, otherwise YAML |
| `YAML` / `YML` | `balances.yml` |
| `JSON` | `balances.json` (imports existing `balances.yml` if the JSON file does not exist) |
| `H2` | Embedded H2 database |
| `SQLITE` | Embedded SQLite database |
| `MYSQL` | MySQL |
| `MARIADB` | MariaDB |
| `POSTGRESQL` | PostgreSQL |

SQL connection details are under `storage.balances.sql`. MySQL and MariaDB default to the existing `storage.mysql` connection fields. When the selected backend is empty, the plugin imports the previous `balances.yml`; when `storage.use_mysql: true`, it can also import the legacy `vault_balances` and `vault_world_balances` tables. The source files/tables are left intact. Select one balance backend at a time. Switching between already-populated new backends (for example, H2 to PostgreSQL or JSON to YAML) is not an automatic copy; back up and migrate those data before changing the type. This setting controls wallet balances; the optional bank-account feature continues to use `bank_balances.yml`.

## GUI layouts and Paper Dialogs

At first startup the plugin creates `plugins/Vault/gui_layout/` with one YAML file per menu: `main.yml`, `settings.yml`, `pay.yml`, `bank.yml`, and `history.yml`. These files control inventory size, item materials, slots, enabled states, and whether supported menus use `INVENTORY` or `DIALOG`. Localized titles, labels, lore and descriptions stay in `plugins/Vault/messages/`. The main Vault and settings menus support Paper Dialog mode; the default is `INVENTORY`. Paper Dialogs require Paper 1.21.7 or newer.

## Messages

All player-facing localized messages are stored in `plugins/Vault/messages/`. Select the primary language with `language` in `config.yml`. Missing keys fall back to `messages_en.yml`.

## Commands

- `/balance` — show balance
- `/pay` — open player/payment menu
- `/pay <player> <amount>` — transfer money
- `/vault` — open the Vault menu
- `/vault reload` — reload plugin configuration and messages
- `/vault top` — show top balances
- `/vault history` — open transaction history
- `/vault withdraw <amount>` — withdraw a physical note
- `/vault offlinepay` — manage offline payment requests (permission-restricted)
- `/eco` — administrative economy commands

## Version 2.1.2

See [WHAT'S-NEW-2.1.2.md](WHAT'S-NEW-2.1.2.md) for this release's changes.
