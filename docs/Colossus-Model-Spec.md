# Patron colossus: model spec

Andy's reference: `Commandment breaker.gif` (project files; a clip of the Dreamshift map by multision, ideas only). It shows a
vast, blank, pale humanoid standing in haze, with a "RUN! n blocks" and "STEPS REMAINING" HUD.

## What we built (our own)
- **Body**: the vanilla humanoid box layout (8x8x8 head, 8x12x4 body, 4x12x4 limbs) via `ChampionModel.createBodyLayer`, so each
  patron keeps its champion's marks (apron and bun, beard and pauldrons, veil and sash, antlers and collar, hood and lantern,
  crown rays and cape, hood and scarf). The Truth's colossus uses the plain humanoid mesh: no mitre, no stole, no idol.
- **Size**: 80 blocks tall (`PatronColossus.HEIGHT`). Aether Giant 30.6, the biggest giant 61.2, the Great Ape ~36.
- **Skin**: `tools/gen_colossus.py`, 64x64, one pale grey-white (236,238,242) with a soft grain and lighter tops; no face.
- **Look**: `entityTranslucentEmissive`, full bright, tinted 30% toward the patron's colour; alpha 190 (strike), 150 (hunt),
  110 (omen); fades in over 30 ticks and out over 40.
- **Moves**: head bowed 0.35 rad; a slow stride (legs +-0.35 rad, arms +-0.2) while walking; the stamp lifts the right leg
  to -0.9 rad over 30 ticks (eased) and drops it in 6, arms thrown out 0.5 rad.

## Differences from the reference
- Ours wears its patron's marks and colour; the reference is one blank figure.
- No "steps remaining" counter; the hunt shows the distance only.
- UNKNOWN: how the reference figure moves (the clip shows it standing).

Scale sheet: project files `art-overhaul/patron-colossus/colossus-scale.png`.
