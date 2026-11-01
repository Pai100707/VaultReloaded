---
title: Support
description: Troubleshooting and bug-report guidance for Vault 2.1.2.
---

# Support & Troubleshooting

## Before reporting an issue

1. Confirm that the server is running Java 21 and Paper 1.21.7+ or a compatible Folia build.
2. Check the server console for the first Vault error, not only the final stack-trace line.
3. Back up `plugins/Vault/` and your SQL database before changing storage settings.
4. Include the Vault version, server implementation/version, Java version, relevant configuration (with passwords removed), and the complete error from `latest.log`.

## Useful checks

- Confirm the configured `storage.balances.type` is supported and its JDBC URL/credentials are correct for SQL backends.
- Confirm that `plugins/Vault/messages/` and `plugins/Vault/gui_layout/` contain readable YAML files.
- When using Folia, verify the server build and inspect scheduler-related errors in the console.
- When using Paper Dialogs, use Paper 1.21.7 or newer.

## Reporting a bug

Provide:

- Vault version (currently 2.1.2).
- Server implementation and exact build number.
- Java version.
- Relevant plugin dependencies.
- Steps to reproduce the problem.
- The complete related log output.
- A sanitized copy of the affected configuration file.

Never attach database passwords, webhook URLs, HMAC secrets, or other credentials.
