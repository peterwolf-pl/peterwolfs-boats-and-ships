# Changelog

## Unreleased

- Double-tap `W` while captaining for a temporary speed boost (higher acceleration and max speed while held).
- Double-tap `A` or `D` for a much sharper turn rate while the key is held.
- Sharp turns lean the hull up to 30° opposite the turn direction (sails and oars follow the heel).
- Added **Lighthouse Light** block item: craft with 6 lanterns + 3 gold ingots. Emits full block light and a rotating dual spotlight beam visible far beyond normal block render distance.
- Lighthouse Light right-click toggles rotating spotlight vs blinking flash; beam visibility is reduced by day and full at night.

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
