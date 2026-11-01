---
title: Languages
description: Editing and adding translations for Vault 2.1.2.
---

# Languages and Messages

All player-facing text is stored in `plugins/Vault/messages/`. The bundled language files are:

- `messages_en.yml` — English fallback
- `messages_de.yml` — German
- `messages_es.yml` — Spanish
- `messages_fr.yml` — French
- `messages_hi.yml` — Hindi
- `messages_ja.yml` — Japanese
- `messages_nl.yml` — Dutch
- `messages_pl.yml` — Polish
- `messages_pt.yml` — Portuguese
- `messages_ru.yml` — Russian
- `messages_tr.yml` — Turkish
- `messages_zh_CN.yml` — Simplified Chinese
- `messages_zh_TW.yml` — Traditional Chinese

Choose a language in `config.yml`:

```yaml
language: en
```

The plugin loads the selected file and falls back to the matching key in `messages_en.yml` when a translation is missing. On startup, new default keys are copied into existing language files without replacing translations already present.

## Add a translation

1. Copy `messages_en.yml` to `messages_<locale>.yml`.
2. Translate the message values, not the YAML keys.
3. Keep placeholders such as `%player%`, `%amount%`, `%page%` and `%pages%` unchanged.
4. Set `language: <locale>` in `config.yml` and restart or reload the plugin.

Do not hard-code player-facing messages in Java. Add message keys to the English resource first, then translate them in the locale files.
