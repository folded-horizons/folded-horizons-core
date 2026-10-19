# Folded Horizons Core

<!-- CI badge (enable after workflow lands on GitHub):
![Build](https://github.com/folded-horizons/folded-horizons/actions/workflows/build.yml/badge.svg)
-->

Core companion mod for the **Folded Horizons** GregTech modpack
(NeoForge, Minecraft **1.21.1**, NeoForge **21.1.65**).

Live repo: [`folded-horizons/folded-horizons`](https://github.com/folded-horizons/folded-horizons).
Pack-side docs (spec, design) live in the pack repo under `docs/`.

## What it is

`foldedhorizons` — pocket grid/registry, Breacher, Root Binder, Dim-Gen,
Trade Post, Temp Keys, walls, expansion. Zero integration code: runs
standalone, with built-in fallbacks (plain-inventory trading, built-in
parties).

Integration modules are separate mods, released separately:

- [folded-horizons-ae2](https://github.com/folded-horizons/folded-horizons-ae2) — AE2 bridge, ME Trade Post backend
- [folded-horizons-gt](https://github.com/folded-horizons/folded-horizons-gt) — GTCEu machine-framework registration, tier detection
- [folded-horizons-ftb](https://github.com/folded-horizons/folded-horizons-ftb) — FTB claims + team identity

Modules import core's API; core imports no module (hard boundary —
see pack docs `companion-mod/architecture.md`). Modules consume core
via the `dev.foldedhorizons:foldedhorizons` maven artifact.

## Building

```sh
gradle build
```

Requires Java 21. Gradle wrapper properties are provided; the wrapper
jar is not committed.
