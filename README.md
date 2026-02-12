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

CreditSystem applies `ui.theme` by generating UI files on disk at startup and on `/credits reload`:

- `Common/UI/Custom/Pages/Credits/_generated/CreditShopEmpty_<hash>.ui`
- `Common/UI/Custom/Pages/Credits/_generated/CreditShopItems_<hash>.ui`

Runtime `CustomUI Set` calls for `Label.Style` (or `Label.Style.FontSize`) are **not used** because they can disconnect clients on this platform.

Use `ui.theme` in `config.json` to control `TextColor` and `FontSize` values that are baked into the generated `.ui` files.

Example:

```json
{
  "ui": {
    "title": "Credit Shop",
    "theme": {
      "title": { "fontSize": 42, "color": "#FFFFFF" },
      "credits": { "fontSize": 22, "color": "#8FAAFC" },
      "selectedCategory": { "fontSize": 20, "color": "#CBD5E1" },
      "categoryButton": { "fontSize": 18, "color": "#FDE047" },
      "closeButton": { "fontSize": 16, "color": "#E2E8F0" },
      "pageIndicator": { "fontSize": 18, "color": "#E5E7EB" },
      "paginationButton": { "fontSize": 16, "color": "#E2E8F0" },
      "itemName": { "fontSize": 20, "color": "#F8FAFC" },
      "itemPrice": { "fontSize": 18, "color": "#FDE047" },
      "itemDescription": { "fontSize": 13, "color": "#CBD5E1" },
      "buyLabel": { "fontSize": 16, "color": "#E2E8F0" }
    }
  }
}
```

After changing theme values, run `/credits reload`, then close and reopen `/creditshop` to see updates.
The generated file name includes a short hash, so each theme change creates a new versioned UI filename for cache-busting.

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

The plugin does not send runtime `Set` commands to `Label.Style` selectors. Theme changes are written into disk `.ui` files instead.

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
