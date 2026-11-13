# Atroxiia stands on the ground
- Atroxiia's body was drawn 24 px (about 1.7 blocks) too low. Its forearms and hands sank under its feet, and its saddle sat well below a rider's seat. When it moved to GeckoLib (#1198), heights were turned upside down without the 24 px lift a Java model needs. The whole body is now raised to where the old model had it, with nothing else changed: its hands rest on the ground and the saddle sits under the rider (`atroxiia.geo.json`, `kingdoms/art/atroxiia.bbmodel`).
- This was found in #1222 and left unfixed. Not yet seen in game.
