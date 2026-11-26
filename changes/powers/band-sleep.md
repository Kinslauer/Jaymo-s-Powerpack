# Bands sleep until you approach
- The Aether and the other bands above and below the Overworld go still, like an unloaded dimension, until a non-spectator player comes within 144 blocks. The Overworld's force-loaded farms keep working, and ordinary worlds stay unchanged.
- Distant leaves stop decaying into piles of sticks and lizards under the islands at spawn. Skyroot lizards only come from leaves in the Aether, including its separate dimension in ordinary worlds.
- Classes: BandSleep, Stacked, BandSleepLevelMixin, BandSleepBlockEntityMixin and AetherAnimals; registered both sleep mixins in jaymospowers.mixins.json. Scheduled block and fluid ticks remain untouched.
- Built by CI; not played. No local build was run; CI results are pending. Not tested in game: approach and departure, force-loaded columns and farms, passengers, block entities, CustomChunks wrapper chaining, separate dimensions or server restart cache clearing.
