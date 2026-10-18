# Folded Horizons Core

Companion mod for the **Folded Horizons** GregTech modpack
(NeoForge, Minecraft **1.21.1**, NeoForge **21.1.65**). Standalone repo for the core mod; integration modules (-ae2/-gt/-ftb) live in their own repos.

Live repo: [`folded-horizons/folded-horizons`](https://github.com/folded-horizons/folded-horizons).
Pack-side docs (spec, design) live in the pack repo under `docs/`.

## Layout — one gradle build, four jars

This repo builds **core** (`foldedhorizons`): pocket grid/registry,
Breacher, Root Binder, Dim-Gen, Trade Post, Temp Keys, walls, expansion —
zero integration code.

Integration modules are separate mods, released separately:

| Repo | Mod id | Ships |
| --- | --- | --- |
| [folded-horizons-ae2](https://github.com/folded-horizons/folded-horizons-ae2) | `foldedhorizons-ae2` | AE2 19.2.x bridge (`AE2Bridge`), ME Trade Post backend |
| [folded-horizons-gt](https://github.com/folded-horizons/folded-horizons-gt) | `foldedhorizons-gt` | GTCEu framework machine registration, GT tier detection |
| [folded-horizons-ftb](https://github.com/folded-horizons/folded-horizons-ftb) | `foldedhorizons-ftb` | FTB Chunks/Teams claim + team identity adapters |

Modules import core's API; core imports no module (hard boundary —
see pack docs `companion-mod/architecture.md`). Modules consume core
via the `dev.foldedhorizons:foldedhorizons` maven artifact.

## Building

```sh
gradle build
```

Requires Java 21. Gradle wrapper properties are provided in `core/`;
the wrapper jar is not committed.
