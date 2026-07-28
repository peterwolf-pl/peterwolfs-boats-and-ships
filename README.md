# Peterwolf's Minecraft Boats and Ships

Fabric mod for Minecraft 26.2 adding three original, wooden, survival-friendly watercraft. They are custom entities: they do not reuse the vanilla boat entity or model.

## Units

| Unit | Seats | Character | Cargo |
| --- | ---: | --- | --- |
| River Skiff | 2 | Fast, nimble river craft with animated oars | — |
| Explorer Sloop | 4 | Single-mast explorer with cream sail and stern wheel | 9 slots |
| Merchant Schooner | 6 | Stable two-mast cargo ship | 27 slots |

## Controls

Place a vessel on water with its item, then **step onto the deck** to board (no right-click). **Right-click** claims the helm — only the helmsman can steer. `W`/`S` apply forward/reverse thrust; `A`/`D` steer. **Double-tap `W`** while sailing for a speed boost (camera eases out — third-person distance + slight FOV pull); **double-tap `A` or `D`** for a much sharper turn (the hull heels up to ~30° opposite the turn). Hold sneak while using the Sloop or Schooner to open its cargo hold. The Explorer Sloop carries two deck barrels; the Merchant Schooner has four supply chests around the mainmast. Movement simulation and collision handling are executed on the server; the client only submits helmsman input and interpolates hull heel, sail and oar animation.

## Lighthouse Light

Craft a **Lighthouse Light** (`6× lantern` + `3× gold ingot`) to crown a coastal tower. **Right-click** cycles three modes: rotating spotlight ray → blinking point light (no beam) → off. Spot beams are faint by day and strong at night; flash blinks light level 15/0; off emits no light.

## Installation

Install Fabric Loader 0.19.3 for Minecraft 26.2, Fabric API 0.153.0+26.2, and Java 25. Place `peterwolfs-boats-and-ships-1.0.4.jar` in the instance `mods` folder.
