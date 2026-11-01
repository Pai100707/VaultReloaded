---
title: Contribute
description: Development setup and contribution guide for Vault 2.1.2 using Gradle Kotlin DSL and Java 21.
---

# Contributing to Vault 2.1.2

## Requirements

- Git
- JDK 21
- Gradle 9.8.2 (the repository includes `gradlew` / `gradlew.bat` launchers)

## Build

Linux / macOS:

```bash
chmod +x ./gradlew
./gradlew clean build
```

Windows:

```bat
gradlew.bat clean build
```

The build uses Gradle Kotlin DSL (`build.gradle.kts`) and produces the plugin jar in `build/libs/`.

The checked-in launcher downloads the pinned Gradle distribution on first use. To replace it with the official Gradle Wrapper scripts and wrapper JAR, install Gradle and run:

```bash
gradle wrapper --gradle-version 9.8.2 --distribution-type bin
```

## Project structure

- `src/main/java/com/paimc/vault/` — plugin implementation.
- `src/main/resources/messages/` — localized player-facing text.
- `src/main/resources/gui_layout/` — default GUI layouts.
- `src/main/resources/config.yml` — plugin configuration defaults.
- `build.gradle.kts` — plugins, repositories, dependencies and build tasks.

## Messages and localization

Player-facing text belongs in `src/main/resources/messages/messages_<locale>.yml`. Avoid hard-coding user-visible labels or chat messages in Java. Add new keys to `messages_en.yml`; other locales can fall back to English until translated. Keep placeholders consistent across locale files.

## GUI layouts

Default layouts are in `src/main/resources/gui_layout/` and are copied to `plugins/Vault/gui_layout/` on first startup. Keep inventory item slots and click handling tied to the same layout keys. GUI text that must be localized belongs in the message files; geometry and material choices belong in the layout YAML.

## Folia compatibility

Use the appropriate Paper/Folia scheduler for the work being performed. Do not introduce Bukkit's global scheduler for entity or region work. Entity operations must run through the entity scheduler; region work must use the region scheduler; independent I/O may use the async scheduler.

## Pull request checklist

- [ ] `./gradlew clean build` succeeds with JDK 21.
- [ ] No user-facing text is hard-coded in Java.
- [ ] New messages have an English fallback and preserve existing placeholders.
- [ ] GUI rendering and click handling use the same configured slots.
- [ ] Entity and region operations are Folia-safe.
- [ ] Existing transaction and balance data formats remain compatible.
- [ ] Release notes are updated when behavior changes.
