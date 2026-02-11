# CreditSystem

Hytale server plugin/mod source project with:

- SQL-backed shared credits economy (`player_credits` table, MySQL/MariaDB)
- `/credits` balance + admin subcommands (`give`, `set`, `remove`)
- `/creditshop` UI page under `Common/UI/Custom/Pages/Credits/CreditShop.ui`
- JSON configuration (`config.json`) including UI title, categories, and DB settings

## Build (no Gradle wrapper)

Use system Gradle:

```bash
gradle build
```

## Fallback compile (plain javac, no binary generation required)

You can compile sources without packaging:

```bash
# Example: compile source and resources for validation only
javac --release 21 -d out $(find src/main/java -name "*.java")
```

(Do not generate or commit jars in this environment.)
