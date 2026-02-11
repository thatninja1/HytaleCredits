# CreditSystem

Hytale server plugin/mod source project with:

- SQL/multi-storage credits economy (`credits` table/collection)
- `/credits` balance + admin subcommands (`give`, `set`, `remove`) + `/credits storage`
- `/creditshop` UI pages under `Common/UI/Custom/Pages/Credits/`
- JSON configuration (`config.json`) with default `storage.type = h2` for zero-config startup
- Automatic fallback to H2 when external storage is unavailable

## Build

Use system Gradle:

```bash
gradle clean build
```

This project builds a single fat jar (`build/libs/CreditSystem-1.0.0.jar`) with runtime dependencies.

## Credit Shop Items (per-category JSON)

Category shop files are created automatically (on first open/load) at:

- `plugins/CreditSystem/shops/vip.json`
- `plugins/CreditSystem/shops/tags.json`

Each file contains item entries keyed by an item id.

One item structure:

```json
{
  "itemIdHere": {
    "name": "Display name shown in the shop",
    "price": 250,
    "description": "Optional long description. Explains what the player gets.",
    "command": "single command example"
  }
}
```

Multiple command structure:

```json
{
  "itemIdHere": {
    "name": "Example Tag",
    "price": 250,
    "description": "Grants the player an example tag.",
    "commands": [
      "say {player} bought a tag!",
      "lp user {player} meta setprefix 100 \"[TAG]\""
    ]
  }
}
```

Schema notes:

- `name` (string, required)
- `price` (number, required)
- `description` (string, optional, can be long)
- `command` (string, optional) **or** `commands` (string array, optional)
- Either `command` or `commands` can be used. If both exist, `commands` is preferred.
- Descriptions are optional; if missing, the description area is blank.

Placeholders supported in command strings:

- `{player}` -> username
- `{uuid}` -> uuid

Pagination behavior in the shop UI:

- 5 items per page
- `Next`/`Prev` buttons appear when needed for additional pages

Starter template behavior:

- New category files include example items with descriptions by default:
  - `VIP Rank` (with a `commands` array example)
  - `Example Tag` (with a single `command` example)
