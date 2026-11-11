# Aether Giants shake and kick up dust at their old strength again
- **Fix**: the last patch multiplied each Aether Giant's shake and dust by its height against a player's (about 17 times), so every step shook the screen by about 19 degrees for everyone within roughly 476 blocks, and a slam by about 51 degrees. Now a normal Aether Giant shakes and kicks up dust as it did before, and only one bigger or smaller than its kind scales up or down, at most twice as hard and as far (`AetherGiant.sizeScale`, `SIZE_SCALE_MIN`/`MAX`).
- Its stamp still shakes the ground, now at a fixed strength (`STAMP_SHAKE`).
- Not tested in game.
