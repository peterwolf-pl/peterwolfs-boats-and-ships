# Changelog

## 1.0.14

### Shore hamlets
- Waterman settlements now sit **at the waterline** instead of on a raised bank: hut floors, the terrace and the pier match the water surface.

## 1.0.13

### Shore hamlets
- Huts that sit on water now stand on log stilts and get a **spruce boardwalk** inland to the bank or village path.
- Six hut looks instead of one: oak, spruce, dark oak, birch, mangrove and bamboo, with different footprints, roofs, windows and small porch details. Bamboo doors stay on every house.

## 1.0.12

### Waterman settlements
- Watermen now live in generated shore hamlets on the bank of a large body of water (ocean, wide river or lake). Inland ponds are skipped.
- Simple oak huts with **flat slab roofs** and **bamboo doors**, crop plots, and a **mandatory wooden port** with docked vessels.
- One in ten settlements is large: more huts, a bigger pier, and a stone **lighthouse** capped with Lighthouse Light.
- Locate with `/locate structure peterwolfs_boats_and_ships:waterman_settlement`. Operators can place one with `/pwboats spawn-settlement [small|large]` while standing on a shore.

## 1.0.11

### Watermen come home
- Stopped watermen spinning in place after leaving the pier. A failed local water-route no longer zeros thrust and flips the rudder.
- They now keep steering toward the atoll or the remembered departure berth; if the hull is jammed they back off and turn instead of circling.
- Unloaded ocean chunks are treated as open water, voyage tickets cover a wider window, and the return clock is no longer cut off mid-ocean.
- After an atoll trade they still walk treasure to the chest beside the bed; if there is no bed they use (or place) a chest at the home port.

## 1.0.10

### Crash fix
- Fixed a server crash (`ArrayIndexOutOfBoundsException`) when a waterman's return route had no intermediate waypoints — the hull now steers toward the port instead of indexing an empty list.

## 1.0.9

### Atoll treasure goes home
- After a waterman returns from an atoll trade, he takes the treasure off the vessel and walks it to the chest beside his bed.
- He prefers his claimed village bed, otherwise the nearest waterside bed, and uses an adjacent chest (or places one next to the bed if none is there).
- Copper, iron, gold, emeralds, diamonds, netherite scrap and Atoll Seeds are stored in that chest instead of remaining in the hold or his pockets.

## 1.0.8

### Multiple riders and safe disembarking
- Enabled the full declared seating capacity: 2 riders in the River Skiff, 4 in the Explorer Sloop and 6 in the Merchant Schooner.
- The first player to right-click an unclaimed vessel becomes the helmsman. Later players board free passenger seats without stealing control.
- Prevented players, villagers and watermen from dismounting while a vessel is moving or stopped in open water.
- A mounted rider may disembark only after the vessel has stopped beside a safe solid shore or pier position.
- Watermen now keep steering through the final port approach, stop the hull at the actual bank or pier, and only then leave the vessel.
- Added a Client GameTest covering three simultaneous riders, first-click steering priority, moving-vessel dismount rejection for a player and villager, and safe pier disembarking.
- Removed the artificial green navigation island from the waterman Client GameTest; the voyage now uses open water and the real port pier.

## 1.0.7

### Water World Atoll trade voyages
- Added optional compatibility with **Water World - The Atoll**; Boats and Ships still runs normally when the atoll mod is absent.
- Watermen frequently seek the nearest atoll, choose a port-facing offshore berth, sail there to trade and return to their remembered home port.
- Long voyages are planned as safe local water-route legs. Each leg respects the current hull width, banks, islands and solid obstacles, while a moving voyage ticket keeps the nearby water loaded.
- At the atoll, the waterman visibly trades with emeralds, happy-villager particles and trade sounds before loading tangible wealth into the vessel cargo and his inventory.
- Returning cargo includes copper, iron blocks, gold blocks, emerald blocks, diamonds and netherite scrap, plus optional Atoll Seeds when the atoll mod provides them.
- After a successful return, the waterman displays an emerald block and waits before planning another trade voyage.

## 1.0.6

### Waterman villager
- Added the **Waterman Spawn Egg** to the mod tab and vanilla Spawn Eggs tab, with English and Polish names.
- Watermen are persistent fisherman villagers that prefer living by water and remember a waterside port.
- They visibly fish at the shore, play casting/catch effects and store real cod, salmon, tropical fish or pufferfish.
- They board any empty vessel from this mod, make a short server-authoritative trip, steer back to the same port and disembark.
- Their autopilot plans a water-only route sized for the current hull, keeps clearance from banks and solid obstacles, never cuts diagonally across land, and recalculates the route if the way becomes blocked.

### Controls & boarding
- **No auto-board**: walking onto the hull never mounts you as a passenger.
- **Right-click only** takes the helm (WASD steering); right-click again or sneak leaves the helm onto the deck. Only the helmsman steers.
- **Walkable deck** like blocks in water: solid collision at deck height so you can step on from a pier and off freely without vehicle lock. Hull hitboxes match the deck (not masts) so approach from a dock works.
- Double-tap `W` while captaining for a temporary speed boost (higher acceleration and max speed while held).
- Double-tap `W` speed boost eases the camera out: third-person distance increases and FOV widens slightly while the boost is held.
- Double-tap `A` or `D` for a much sharper turn rate while the key is held.
- Sharp turns lean the hull up to 30° opposite the turn direction (sails and oars follow the heel).

### Survival crafting
- Confirmed shaped crafting recipes for all vessels and the lighthouse light (crafting table).
- Added recipe-book unlock advancements so recipes appear when you obtain a key ingredient (oak planks / white wool / spruce planks / gold ingot).
- **River Skiff**: 2 iron nugget + 1 oak planks + 5 stick.
- **Explorer Sloop**: 2 chain + 1 white wool + 2 string + 1 barrel + 3 oak planks.
- **Merchant Schooner**: 2 chain + 1 barrel + 3 white wool + 3 spruce planks.
- **Lighthouse Light**: 6 lantern + 3 gold ingot.

### Lighthouse Light
- Added **Lighthouse Light** block: full block light and a long-range rotating dual spotlight beam.
- Right-click cycles rotating spotlight → blinking point light (no ray; light 15 on / 0 off) → off.
- Spot beam visibility is reduced by day and full at night.

### Deck props
- Merchant Schooner deck: four supply chests placed around the mainmast.
- Explorer Sloop deck: two supply barrels beside the mast (replacing the single mid-deck chest prop).

## 1.0.4

- Added furled sails when stationary: sails automatically roll up under yardarms when ships are at rest, and unfurl fully when moving.
- Fixed yardarm position and mast geometry: yardarms are now mounted at the top of sails, thinner topmast extensions (stengi) extend higher above the yardarms, and rolled-up sails collapse tight against the yardarms.
- Overhauled rowboat oars: re-anchored oars inside gunwale rowlocks, oriented paddle blades vertically, and corrected dip angles so blades submerge deep underwater during rowing strokes.
- Adjusted seating positions: captain now sits on the rear bench near the stern on rowboats and stands at the stern helm wheels on sailing ships.
- Fixed deck water clipping and open hulls: raised floor floating offsets above water line and enclosed hulls with solid bow, stern, and side walls.

## 1.0.3

- Fixed ships freezing when boarded: client no longer claims local authority over server-simulated hulls, so captain WASD input and server position sync apply correctly.

## 1.0.2

- Added vanilla-style client interpolation for server-authoritative ship position updates.
- Prevented empty ships from being pushed by players.

## 1.0.1

- Replaced promotional-image entity textures with proper pixel-art wood, metal and sail texture atlases.
- Made captain input resilient across client/server connections by continuously sending client controls and falling back to server player input.

## 1.0.0

- Added River Skiff, Explorer Sloop and Merchant Schooner as original steerable entities.
- Added server-authoritative water movement, captain input networking, multi-passenger seating, hull collision handling, and client-side sail/oar/heel animation.
- Added survival recipes, cargo holds, translations, creative tab, entity/item assets and promotional artwork.
