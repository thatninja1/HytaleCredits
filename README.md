# CreditSystem

Hytale server plugin/mod source project with:

- SQL/multi-storage credits economy (`credits` table/collection)
- `/credits` balance + admin subcommands (`give`, `set`, `remove`) + `/credits storage`
- `/creditshop` UI page under `Common/UI/Custom/Pages/Credits/CreditShop.ui`
- JSON configuration (`config.json`) with default `storage.type = h2` for zero-config startup
- Automatic fallback to H2 when external storage is unavailable

## Build

Use system Gradle:

```bash
gradle clean build
```

This project builds a single fat jar (`build/libs/CreditSystem-1.0.0.jar`) with runtime dependencies.
