# Peterwolf's Minecraft Boats and Ships

Fabric mod for Minecraft 26.2 adding three original, wooden, survival-friendly watercraft. They are custom entities: they do not reuse the vanilla boat entity or model.

## Units

| Unit | Seats | Character | Cargo |
| --- | ---: | --- | --- |
| River Skiff | 2 | Fast, nimble river craft with animated oars | — |
| Explorer Sloop | 4 | Single-mast explorer with cream sail and stern wheel | 9 slots |
| Merchant Schooner | 6 | Stable two-mast cargo ship | 27 slots |

## Controls

Place a vessel on water with its item, then use it to board. The first passenger is captain. `W`/`S` apply forward/reverse thrust; `A`/`D` steer. Hold sneak while using the Sloop or Schooner to open its cargo hold. Movement simulation and collision handling are executed on the server; the client only submits captain input and interpolates hull heel, sail and oar animation.

## Installation

Install Fabric Loader 0.19.3 for Minecraft 26.2, Fabric API 0.153.0+26.2, and Java 25. Place `peterwolfs-boats-and-ships-1.0.4.jar` in the instance `mods` folder.
