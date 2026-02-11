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
- `ui.theme` (optional text style overrides)
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

### UI Theme text styles (`ui.theme`)

Each style object supports:

- `color` in hex format `#RRGGBB`
- `fontSize` integer, valid range `8..72`

Invalid values are ignored with a warning and safe defaults are used.

Example:

```json
{
  "ui": {
    "title": "Credit Shop",
    "theme": {
      "title": { "color": "#E5E7EB", "fontSize": 46 },
      "credits": { "color": "#93C5FD", "fontSize": 24 },
      "selectedCategory": { "color": "#CBD5E1", "fontSize": 20 },
      "categoryButton": { "color": "#FDE047", "fontSize": 18 },
      "closeButton": { "color": "#E2E8F0", "fontSize": 16 },
      "pageIndicator": { "color": "#808080", "fontSize": 18 },
      "paginationButton": { "color": "#E2E8F0", "fontSize": 16 },
      "itemName": { "color": "#F8FAFC", "fontSize": 20 },
      "itemPrice": { "color": "#FDE047", "fontSize": 18 },
      "itemDescription": { "color": "#CBD5E1", "fontSize": 13 },
      "buyLabel": { "color": "#E2E8F0", "fontSize": 16 }
    }
  }
}
```

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

### Category-level + item-level style overrides

You can define optional category defaults via top-level `meta.styles`, then override one item via `itemN.styles`.

Full example:

```json
{
  "meta": {
    "styles": {
      "itemName": { "color": "#9AE6B4", "fontSize": 21 },
      "itemPrice": { "color": "#F6E05E", "fontSize": 18 },
      "itemDescription": { "color": "#A0AEC0", "fontSize": 13 },
      "buyLabel": { "color": "#E2E8F0", "fontSize": 16 }
    }
  },
  "item1": {
    "name": "VIP Rank",
    "price": 1000,
    "description": "Unlocks VIP chat prefix and extra server perks.",
    "commands": [
      "lp user {player} parent add vip",
      "say {player} unlocked VIP!"
    ],
    "styles": {
      "name": { "color": "#FFFFFF", "fontSize": 22 },
      "price": { "color": "#FDE047", "fontSize": 19 },
      "description": { "color": "#CBD5E1", "fontSize": 14 },
      "buy": { "color": "#E2E8F0", "fontSize": 16 }
    }
  },
  "item2": {
    "name": "Example Tag",
    "price": 250,
    "description": "Grants a cosmetic tag shown in chat.",
    "command": "say {player} bought a tag!"
  }
}
```

### Style priority order

The runtime style merge order is:

1. per-item styles (`itemN.styles.*`)
2. category meta styles (`meta.styles.*`)
3. global config theme (`ui.theme.*`)
4. built-in UI defaults from `.ui` files

### Ordering and pagination

- Only keys matching `item<number>` are treated as shop items.
- Items are sorted by numeric suffix (`item1`, `item2`, ...), deterministic every load.
- 5 items per page.
- `Prev` / `Next` controls appear when needed.

### Runtime CustomUI styling safety

Hytale CustomUI does not support setting Style structs directly at runtime. This plugin applies font size + color by setting Style subfields (`.Style.FontSize`, `.Style.TextColor`, `.Style.Alignment`).

### Description wrapping

Hytale Custom UI in this project does not support `TextWrap`/`ScrollView`.
Descriptions are wrapped in Java before rendering:

- Wrapping is automatic and approximate (word-aware with hard split fallback).
- Long text is truncated with `...` when it exceeds card limits.
- Increasing description `fontSize` reduces characters-per-line automatically.

### Starter template behavior

When a category file is missing, CreditSystem auto-generates one that includes:

- `meta.styles` example
- `item1` with description + `commands` array + styles example
- `item2` with description + single `command`
