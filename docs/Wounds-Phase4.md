# Wounds phase 4: medieval medicine (Powers 0.14.4, Kingdoms 0.7.102; renumbered after a merge)

Built on phases 1-3 (`core/wounds`: Wounds, BodyPart, BodyPlan, HitRouting, Downed, Breaks, WoundsCommand). Plan:
`docs/Wounds-Plan.md`, "Healing" (the medieval kit, all of it; Andy 2026-10-20: "I dont want any modern medicine").
Kitchen 0.5.0 (a required dependency of Powers) already has `Brewing.STRONG_SPIRITS` and `Drunk`.

Two tracks, run side by side, touching different files:

## Track A: Powers (`core/wounds/` + client), version 0.14.4

### Bleeding (new, needed by the kit)
- A part can **bleed**: a hit from a blade or a projectile that takes at least `BLEED_FROM` (2) points from a part
  opens a cut there (attachment `jaymospowers:wounds_bleeding`, int bitmask, persistent, synced to the owner). A
  severed stump bleeds heavily (`STUMP_BLEED`).
- A bleeding part loses `BLEED_DAMAGE` (0.5) every `BLEED_TICKS` (80) through `Wounds.hurtPart` with a
  `jaymospowers:bleeding` damage type (JSON like `damage_type/wounds.json`); a cut stops by itself after
  `BLEED_STOP_TICKS` (600) unless it is a stump. Blood drip particles (copy an existing blood particle use, e.g.
  `BladeBlood`). Bosses and bloodless types (`BladeBlood`'s tag) never bleed.

### Festering
- An open cut that is not cleaned can fester: each bleed tick on a cut from a zombie's hit or bandaged with a rag
  rolls `FESTER_CHANCE`; festering parts (attachment bitmask) can't heal above their current fraction, and the
  owner gets Weakness and a slow `FEVER_DAMAGE` every `FEVER_TICKS`, plus a fever shimmer on the client (simple
  tint like phase 2's DownedHud). Cured by pouring strong spirits (Kitchen's item, sneak-use on a target or on
  yourself while looking down, or through the treatment below) or a honey bottle on the part. Left for
  `FESTER_LIMB_TICKS` (a Minecraft day) on a limb: the limb is lost (Dismember's lose) to save the body; on head or
  body it keeps going until cured or death.

### Items (registered in Powers, "Medicine" creative tab or an existing Powers tab; vanilla-style 16x16 icons painted
with a small `tools/vanilla_style/medicine.py` using `tools/vanilla_style/vstyle.py` ramps, as Kitchen's
`brewing.py` did)
| id | made from (shaped/shapeless recipe JSON) | treats (see Treatment) |
|---|---|---|
| `linen_bandage` (x4) | string + white wool | heals the part over time (`BANDAGE_HEAL` over `BANDAGE_TICKS`), stops bleeding |
| `rag` (x2) | any wool or leather | as a bandage, half as well; the part may fester |
| `splint` | 2 sticks + string (or leather) | sets a broken arm or leg (Breaks' set), lifts most of the debuff while worn (Breaks reads a "splinted" state), slightly slower while worn; clears when the break heals |
| `willow_bark_tea` | stripped log (any) + water bottle + bowl | after `TEA_DELAY`, hides pain (Breaks' slowness/weakness modifiers suspended) for `TEA_TICKS` |
| `poppy_syrup` | poppy + sugar + Kitchen's strong spirits + glass bottle | strong pain relief for longer; a downed one comes round to crawling (Downed: not passed out); adds addiction (below); two within `OVERDOSE_TICKS` knocks the drinker out (Downed passed out for a while, players too) |
| `smelling_salts` (4 uses, damage bar) | glass bottle + bone meal + gunpowder | on a downed one: gets them up on the spot (half a heart in head and body, Speed I for 5 s, Nausea after); on one who bled out within `SWOON_TICKS` (players: a passed-out player; mobs: within the window after death is not possible, so for mobs this is only the downed case) a `ROUSE_CHANCE` to wake them, using the whole vial |
| `needle_and_thread` (durability 8) | iron nugget + string | stitches a bleeding part: stops bleeding and lets it heal past half; hurts the part by 1 |
| `leeches` (jar) | glass bottle + slime ball (period stand-in) + water bottle | costs 1 health, dulls pain like the tea for longer |

Strong spirits (Kitchen) as medicine: pouring (sneak + use on a part through Treatment) cleans festering and stops
festering from starting on that part for a while; drinking keeps Kitchen's effects and also dulls pain (Breaks'
modifiers suspended while `Drunk.level >= 4`).

Cautery: holding a sword or axe that is heated (an item component or custom data flag `jaymospowers:heated` set by
using the blade on a lit campfire/fire/lava block, which cools after `HEAT_TICKS`; or any blade with Fire Aspect)
and treating a part: stops all bleeding on it at once (the only way to stop a stump), burns the part for
`CAUTERY_BURN`.

### Treatment (all medicine)
- **Using it:** right-click yourself (use in the air) or another creature with a medicine item opens the body screen
  to pick a part; sneak + use treats the worst part that the item can help (no screen). If a screen proves too much
  without a compiler, a radial-free fallback is fine: use cycles the target part (shown on the overlay) and holding
  use treats it.
- **Treatment takes time:** a channel (hold use) of `TREAT_TICKS` per item (bandage 40, splint 60, needle 60, salts
  20, cautery 20), shown as an overlay bar, cancelled by moving more than 0.5 blocks or being hit, as Downed's
  revive does. Server-authoritative (packets only carry the chosen part).
- The body screen: the phase 1 HUD silhouette drawn large, each part clickable, with its state (health, broken,
  bleeding, festering, splinted, bandaged) as tooltip lines.

### Addiction (poppy syrup)
- Hidden per-player value (attachment, persistent, not synced): +`POPPY_ADDICTION` per dose, decays slowly. Above
  `CRAVING_FROM`, missing a dose for `WITHDRAWAL_TICKS` brings withdrawal: shakes (the client sway Kitchen's
  `DrunkSway` uses, or a small view jitter), Nausea pulses, Weakness, and an overlay line about craving. A dose
  relieves it.

### Downed and smelling salts tie-in
- `Downed` gets public `wake(LivingEntity, boolean passedOut)` / `rouse` helpers the items call. Food revive stays.

### Client
- Poppy haze (a warm tint while under poppy), smelling-salt jolt (white flash for a few frames), fever shimmer.

### Release
Powers 0.14.4, MODLOG entry, mark phase 4 in `docs/Wounds-Plan.md`, lang in `lang/parts/wounds.json`.

## Track B: Kingdoms (kingdoms/ only), next patch version

Kingdoms can't compile against Powers: refer to Powers' items by registry id (`jaymospowers:linen_bandage`, etc.,
looked up in `BuiltInRegistries.ITEM` and skipped if absent) and to Wounds state through scoreboard tags only.
- **Clerics are barber-surgeons:** cleric villagers (and Kingdoms' healer-like folk if any) sell linen bandages,
  splints, smelling salts, leeches and Kitchen's strong spirits (`jaymoskitchen:strong_spirits`) for emeralds (add to
  their trades the way Kingdoms already adds trades). Right-clicking a cleric with emeralds while hurt already sets
  bones via Powers' Fractures (keep that).
- **Villagers tend with bandages:** when Kingdoms' tending (Downed.java's tend) finishes on a downed one, it also
  adds the scoreboard tag `jaymospowers_bandaged` (Powers reads it, removes it, stops that body's bleeding and gives
  a bandage's heal). Powers track A must read `jaymospowers_bandaged`.
- **Taverns and festivals sell spirits:** tavern keepers / festival stalls (whatever Kingdoms has: grep Buildings
  tavern, Festivals) offer ale and strong spirits from Kitchen by id.
- Kingdoms version bump and `kingdoms/MODLOG.md`.

Both tracks: tuning numbers as `static final` constants at the top of each class; copy every API shape from code
that already compiles in this repo; no invented APIs.
