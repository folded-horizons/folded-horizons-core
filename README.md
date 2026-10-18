# Folded Horizons — companion mod monorepo

Companion mod for the **Folded Horizons** GregTech modpack
(NeoForge, Minecraft **1.21.1**, NeoForge **21.1.65**).

Live repo: [`folded-horizons/folded-horizons`](https://github.com/folded-horizons/folded-horizons).
Pack-side docs (spec, design) live in the pack repo under `docs/`.

## Layout — one gradle build, four jars

| Subproject | Mod id | Ships |
| --- | --- | --- |
| `core/` | `foldedhorizons` | Pocket grid/registry, Breacher, Root Binder, Dim-Gen, Trade Post, Temp Keys, walls, expansion — zero integration code |
| `ae2/` | `foldedhorizons-ae2` | AE2 19.2.x bridge (`AE2Bridge`), ME Trade Post backend |
| `gt/` | `foldedhorizons-gt` | GTCEu framework machine registration, GT tier detection |
| `ftb/` | `foldedhorizons-ftb` | FTB Chunks/Teams claim + team identity adapters |

Modules import core's API; core imports no module (hard boundary —
see pack docs `companion-mod/architecture.md`).

## Building

```sh
gradle build          # all four jars → <module>/build/libs/
gradle :core:build    # just core
```

Requires Java 21. Gradle wrapper properties are provided in `core/`;
the wrapper jar is not committed.
