# Peterwolf's Minecraft Boats and Ships

Fabric mod for Minecraft 26.2 adding three original, wooden, survival-friendly watercraft. They are custom entities: they do not reuse the vanilla boat entity or model.

## Units

| Unit | Seats | Character | Cargo |
| --- | ---: | --- | --- |
| River Skiff | 2 | Fast, nimble river craft with animated oars | — |
| Explorer Sloop | 4 | Single-mast explorer with cream sail and stern wheel | 9 slots |
| Merchant Schooner | 6 | Stable two-mast cargo ship | 27 slots |

## Waterman villager

The **Waterman Spawn Egg** is available in the mod tab and the vanilla Spawn Eggs tab. A waterman is a persistent fisherman villager who prefers shore positions, remembers a waterside port, visibly fishes and stores the catch. When an empty River Skiff, Explorer Sloop or Merchant Schooner is nearby, he walks aboard, takes a server-driven cruise and steers back to the same port. He slows for the final approach, stops beside the actual bank or pier and only then disembarks. His autopilot plans across connected water, accounts for the width of the current hull, keeps clear of banks and solid obstacles, and recalculates the route instead of steering through a new obstruction.

With the optional **Water World - The Atoll** mod installed, watermen frequently turn these cruises into long trading voyages. They find the nearest atoll, approach an offshore berth from the port side, visibly trade there, load the ship and their inventory with copper, iron, gold, emeralds, diamonds, netherite scrap and occasional Atoll Seeds, then return to their original port displaying an emerald block. After disembarking they carry that treasure to the chest beside their bed. Long routes are divided into safe locally checked legs and keep nearby voyage chunks active while the vessel is underway.

## Waterman settlements

Hamlets generate on the **shore of a large body of water**. They are never placed inland or on a tiny pond: the generator requires a wide, deep stretch of water and a usable bank.

Every settlement has:

- Simple **oak huts** with **flat slab roofs** and **bamboo doors**, each with a bed and a chest
- **Crop plots** (wheat, carrots, potatoes) with irrigation
- A **port** — spruce pier, lanterns, and docked River Skiff / Explorer Sloop vessels (Merchant Schooner on large hamlets)
- Resident watermen who remember that port

**One in ten** settlements is large and includes a stone **lighthouse** topped with a Lighthouse Light. Find one with `/locate structure peterwolfs_boats_and_ships:waterman_settlement`. Operators can spawn a test hamlet with `/pwboats spawn-settlement [small|large]` while standing on a shore.

## Survival crafting

All items craft in a **crafting table**. Recipes unlock in the recipe book when you pick up a key ingredient.

| Result | Pattern | Materials |
| --- | --- | --- |
| **River Skiff** | `I P I` / `S   S` / `S S S` | 2 iron nuggets, 1 oak planks, 5 sticks |
| **Explorer Sloop** | `C W C` / `S B S` / `O O O` | 2 chain, 1 white wool, 2 string, 1 barrel, 3 oak planks |
| **Merchant Schooner** | `C B C` / `W W W` / `O O O` | 2 chain, 1 barrel, 3 white wool, 3 spruce planks |
| **Lighthouse Light** | `L L L` / `G G G` / `L L L` | 6 lanterns, 3 gold ingots |

```
River Skiff          Explorer Sloop       Merchant Schooner    Lighthouse Light
I P I                C W C                C B C                L L L
S   S                S B S                W W W                G G G
S S S                O O O                O O O                L L L
```

## Controls

Place a vessel on water with its item. The hull is a **solid walkable deck** — step on from a pier like normal blocks, with no automatic mounting. The first player to **right-click** an unclaimed vessel takes the helm; every later right-click boards a free passenger seat without changing the helmsman. Capacity is 2 riders in the River Skiff, 4 in the Explorer Sloop and 6 in the Merchant Schooner. Only the helmsman can steer: `W`/`S` thrust, `A`/`D` rudder. Mounted players, villagers and watermen cannot jump out while the vessel is moving or stopped in open water: stop beside a solid bank or pier first, then dismount. **Double-tap `W`** for a speed boost (camera eases out); **double-tap `A` or `D`** for a sharper turn (hull heels up to ~30°). Hold sneak while using the Sloop or Schooner to open its cargo hold. The Explorer Sloop carries two deck barrels; the Merchant Schooner has four supply chests around the mainmast. Movement and dismount permission are server-authoritative; the client submits helmsman input and interpolates animations.

## Lighthouse Light

Craft a **Lighthouse Light** (`6× lantern` + `3× gold ingot`) to crown a coastal tower. **Right-click** cycles three modes: rotating spotlight ray → blinking point light (no beam) → off. Spot beams are faint by day and strong at night; flash blinks light level 15/0; off emits no light.

## Installation

Install Fabric Loader 0.19.3 for Minecraft 26.2, Fabric API 0.153.0+26.2, and Java 25. Place `peterwolfs-boats-and-ships-1.0.12.jar` in the instance `mods` folder. **Water World - The Atoll** is optional and enables the atoll trade voyages.
