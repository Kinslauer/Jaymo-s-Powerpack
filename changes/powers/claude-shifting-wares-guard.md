# Shifting Wares no longer crashes the server on VillagersPlus professions
- **Fix**: Shifting Wares' daily trade reroll crashed the server tick (NoSuchElementException in `SlotRerollSource.FromSet`) for a villager whose profession names a trade set that isn't registered. VillagersPlus' miner, alchemist, oceanographer, occultist and horticulturist name `villagersplus:<job>/level_1..5`, which its jar never defines. Such a villager now keeps its trades and restocks them the vanilla way instead of rerolling; professions whose sets exist reroll as before.
- Class: `mixin/shiftingwares/TradeShufflerMixin` (compat mixin, `@Pseudo`, skipped when Shifting Wares isn't there).
- Built by CI; not played.
