# CreditSystem

Hytale server plugin/mod source project with:

- SQL/multi-storage credits economy (`credits` table/collection)
- `/credits` balance + admin subcommands (`give`, `set`, `remove`, `reload`, `storage`)
- `/creditshop` UI pages under `Common/UI/Custom/Pages/Credits/`
- JSON configuration (`plugins/CreditSystem/config.json`) with default `storage.type = h2`
- Automatic fallback to H2 when external storage is unavailable

## Build

Use system Gradle:

```bash
gradle clean build
```

This project builds a single fat jar (`build/libs/CreditSystem-1.0.0.jar`) with runtime dependencies.

## config.json

Main config path:

- `plugins/CreditSystem/config.json`

Key structure:

- `ui.title`
- `currencyName`
- `categories[]`
- `storage` (current schema)
- `debug`

Storage schema (current):

```json
{
  "storage": {
    "type": "h2",
    "h2": { "file": "plugins/CreditSystem/credits" },
    "sqlite": { "file": "plugins/CreditSystem/credits.db" },
    "mysql": {
      "host": "127.0.0.1",
      "port": 3306,
      "database": "creditsystem",
      "username": "root",
      "password": "password",
      "useSSL": false
    },
    "mariadb": {
      "host": "127.0.0.1",
      "port": 3306,
      "database": "creditsystem",
      "username": "root",
      "password": "password",
      "useSSL": false
    },
    "postgresql": {
      "host": "127.0.0.1",
      "port": 5432,
      "database": "creditsystem",
      "username": "postgres",
      "password": "password",
      "ssl": false
    },
    "mongodb": {
      "uri": "mongodb://user:pass@127.0.0.1:27017",
      "database": "creditsystem"
    }
  }
}
```

### UI Styling (colors/font sizes)

Runtime styling via CustomUI `Set` on label styles is unsafe in this project and can disconnect clients.
Do **not** try to set `Label.Style` or `Label.Style.FontSize` at runtime.

To change colors/font sizes safely, edit the UI template files directly:

- `src/main/resources/Common/UI/Custom/Pages/Credits/CreditShopEmpty.ui`
- `src/main/resources/Common/UI/Custom/Pages/Credits/CreditShopItems.ui`

Use style entries exactly like:

```ui
Style: (FontSize: 20, Alignment: Center, TextColor: #F8FAFC);
```

Supported alignment tokens in this project are `Center` and `Start`.

## Credit Shop Items (per-category JSON)

Per-category files live at:

- `plugins/CreditSystem/shops/<categoryKey>.json`
- Example: `plugins/CreditSystem/shops/ranks.json`

### Item schema

- `name` (string, required)
- `price` (number, required)
- `description` (string, optional)
- `command` (string, optional) or `commands` (string array, optional)
- If both `command` and `commands` exist, `commands` is preferred.

Placeholders supported in commands:

- `{player}` → username
- `{uuid}` → uuid

### Ordering and pagination

- Only keys matching `item<number>` are treated as shop items.
- Items are sorted by numeric suffix (`item1`, `item2`, ...), deterministic every load.
- 5 items per page.
- `Prev` / `Next` controls appear when needed.

### Runtime CustomUI styling safety

Never send runtime style Set commands in production for this UI (for example `.Style` or `.Style.FontSize`) because they can disconnect players. Keep text color/font/alignment changes inside the `.ui` template files only.

### Description wrapping

Hytale Custom UI in this project does not support `TextWrap`/`ScrollView`.
Descriptions are wrapped in Java before rendering:

- Wrapping is automatic and approximate (word-aware with hard split fallback).
- Long text is truncated with `...` when it exceeds card limits.
- Increasing description `fontSize` reduces characters-per-line automatically.

### Starter template behavior

When a category file is missing, CreditSystem auto-generates one that includes:

- `item1` with description + `commands` array example
- `item2` with description + single `command`
