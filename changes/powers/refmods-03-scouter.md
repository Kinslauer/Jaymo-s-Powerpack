# Scouter: hold to scan, full readings, marks and tracking

The scouter now reads for everyone and does what Wan's Bio Scanner does (its idea only, our own code), in the scouter's own green readout, with no commands and no chat text.

- Hold the new Scouter key (unbound until picked in Controls; tap and hold): the lens zooms a little and the reading fills in line by line under the power level. Let go early and you keep what came through. A soft chime sounds when a reading is complete.
- Creatures: health, strike, speed, armour, young and tame. Horses read speed, jump height and temper from the vanilla horse's own attributes (small helper `ZFighterScanHorse`, ready to point at another horse mod). People read their realm and mood (from Kingdoms). A Z Fighter's forms you have seen (unmet ones are blanks), an alien's growth, a dead walker's road to the horde (alone, in a horde, its captain).
- Plants and living blocks: crops, saplings, bee nests, turtle and sniffer eggs, coral, moss: what stage, what it needs, about how long.
- Open sky or ground: heat or cold, how harsh the land is, sky and light.
- A full scan of a beast counts as studying it: a kind with no foe-knowledge star gets one, and a Druid learns the shapes that beast is kin to.
- Tap the key after a full scan to mark that creature: a chevron at the lens's edge points to it, with its distance, and it shows outlined through walls for five minutes or until lost. One mark at a time (the only scouter there is has no better tier; `ZFighterScan.markLimit` is where a better one would raise it). A tap again lets it go. Something hiding its ki can't be scanned or marked.
- Without ki the scouter still reads: the panel shows the name and kind and no power level.

Main classes: `ZFighterScan`, `ZFighterScanInfo`, `ZFighterScanHorse`, `ZFighterScanKingdoms`, `ZFighterScanClient`, `ZFighterScanZoomMixin`; packets in `ZFighterSensesNet`.

Not tested in game (compiled only): the zoom, the chevron placement, the chime, the HUD panel spacing under the power-level panel, the crop time estimates.
