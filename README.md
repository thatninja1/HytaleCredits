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

CreditSystem uses two fixed CustomUI document slots (same folder, no subfolders):

- `Pages/Credits/CreditShopEmpty-v0.ui`
- `Pages/Credits/CreditShopEmpty-v1.ui`
- `Pages/Credits/CreditShopItems-v0.ui`
- `Pages/Credits/CreditShopItems-v1.ui`

Theme values from `config.json` (`ui.theme.*`) are applied by rewriting the matching disk slot templates:

- `Common/UI/Custom/Pages/Credits/CreditShopEmpty-v0.ui`
- `Common/UI/Custom/Pages/Credits/CreditShopEmpty-v1.ui`
- `Common/UI/Custom/Pages/Credits/CreditShopItems-v0.ui`
- `Common/UI/Custom/Pages/Credits/CreditShopItems-v1.ui`

On startup, both slots are generated. On `/credits reload`, the plugin flips active slot (`v0 ↔ v1`) and rewrites the newly active slot so reopening `/creditshop` uses a new document path and refreshes styles safely.

Runtime `CustomUI Set` calls for `Label.Style` (or `Label.Style.FontSize`) are **not used** because they can disconnect clients on this platform.

Example `config.json` snippet:

```json
{
  "ui": {
    "title": "Credit Shop",
    "theme": {
      "title": { "fontSize": 46, "color": "#808080" },
      "credits": { "fontSize": 24, "color": "#808080" },
      "selectedCategory": { "fontSize": 20, "color": "#808080" },
      "categoryButton": { "fontSize": 18, "color": "#808080" },
      "closeButton": { "fontSize": 16, "color": "#808080" },
      "pageIndicator": { "fontSize": 18, "color": "#808080" },
      "paginationButton": { "fontSize": 16, "color": "#808080" },
      "itemName": { "fontSize": 20, "color": "#808080" },
      "itemPrice": { "fontSize": 18, "color": "#808080" },
      "itemDescription": { "fontSize": 13, "color": "#808080" },
      "buyLabel": { "fontSize": 16, "color": "#808080" }
    }
  }
}
```

After changing theme values, run `/credits reload`, then close and reopen `/creditshop` to see updates. If the client still shows old styles, relog may be required due to client-side UI caching.

## Credit Shop Items (per-category JSON)

Per-category files live at:

- `plugins/CreditSystem/shops/<categoryKey>.json`
- Example: `plugins/CreditSystem/shops/ranks.json`

Example `ranks.json`:

```json
{
  "item1": {
    "name": "Cadet Rank",
    "price": 1000,
    "description": "Unlocks the Cadet rank on Skyblock.",
    "command": "lp user {player} parent add cadet server=skyblock"
  },
  "item2": {
    "name": "Veteran Rank",
    "price": 2500,
    "description": "Unlocks the Veteran rank and perks.",
    "commands": [
      "lp user {player} parent add veteran server=skyblock",
      "broadcast {player} purchased Veteran rank"
    ]
  }
}
```

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
