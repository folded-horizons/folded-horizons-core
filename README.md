# Folded Horizons Core

[![Build](https://github.com/folded-horizons/folded-horizons-core/actions/workflows/build.yml/badge.svg)](https://github.com/folded-horizons/folded-horizons-core/actions/workflows/build.yml)

Core companion mod for the **Folded Horizons** GregTech modpack
(NeoForge, Minecraft **1.21.1**, NeoForge **21.1.65**).

Live repo: [`folded-horizons/folded-horizons`](https://github.com/folded-horizons/folded-horizons).
Pack-side docs (spec, design) live in the pack repo under `docs/`.

## What it is

`foldedhorizons` — pocket grid/registry, Breacher, Root Binder, Dim-Gen,
Temp Keys, walls, expansion. Zero integration code: runs standalone,
with built-in fallbacks (crate-based lobby trading, built-in parties).
Networked ME trading is provided by the AE2 module; the Trade Post's
crate flow works without any module.

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
