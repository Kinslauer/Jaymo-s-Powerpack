# Spirits: a faint warrior that manifests when it speaks
- **The look** (Andy, after Johnny Silverhand in Cyberpunk 2077: "a faint but visible warrior"): every spirit that is out
  now shows as a whole figure you can half-see, standing over its keeper's back, a translucent body under the aura's shells
  so it reads in daylight, flaring when its arms strike. Only the mana attuned (or those reading magic) see it, any
  attuned player sees anyone's spirit, the same rule as the arms.
- **Manifesting**: when a spirit speaks, and when it levels up, it steps out and floats in front of its
  keeper, a little to one side, facing them at about a person's size, nodding and making its point with its own hands
  while the line is said. It forms and goes with a glitch: it jolts, drops out, splits into cyan and magenta, and data
  rain falls through it. The higher its level the more solid it is (half at level 1, full at 20) and the longer it holds
  (3.5 s, plus 0.2 s a level). A level up holds longest, fully solid, with both arms flung up, a ring of light and a
  chime. It keeps its spot while it talks, and blinks back in front of you if you walk off. In first person you see it
  in front of you; its arms over your back stay away while it stands there. The chat line still prints.
- **No legs** (Andy: "the lower half should be wispy"): below the waist every figure thins into trails of smoke, and
  manifested it floats with the last wisp just off the ground.
- **The Grue fights back where you can see it**: each time it lashes at you, your spirit and the Grue appear in front of
  you and trade blows for about three seconds, both glitching, a crack and red sparks at each blow. The stronger the lash,
  the redder your spirit turns and the further the Grue drives it back toward you. The Quiet One fights with its arms alone.
  Only the keeper sees it (the lash is sent to them alone); the darkness the Grue throws in its trial sets it off too.
- **The Grue itself** is ghastly and winged: a horned skull under a torn hood, a starved ribcage, long clawed arms, great
  ragged bat wings and smoke below the waist, a dark see-through body with a glowing red edge. In its trial the boss and its
  shades are drawn as the Grue (the evoker and vexes it wears are hidden; the name and boss bar stay), hovering on its
  wingbeats and clawing at you when you're close, glitching when hit, the same way the spirits animate. In its barrage finale its clawed fists pound as fast as your spirit's.
- **Arms and figure together**: the figure over your back now shows only while its arms do and goes with them, the arms
  held out from it at rest so they never hang into you. They fade shortly after a move, show for a moment now and then on
  their own (about one glimpse every 1-2 minutes, the same for every viewer), and come up as soon as you hold the spirit
  key to call a move. Grimsby's blade and Thessaly's glove stay as they were: always up while the spirit is out, at their own rest angle (so their faint figure stays up with them).
- **Speech bubble**: while a spirit stands out talking, its line floats over its head in a bubble in its colour (as well as
  in chat). Only its keeper is sent the line, so only they see the bubble.
- **The Quiet One** never shows a body: only its large arms, as before.
- **Upgraded figures**: Grimsby is a dead traveller (bones, a tattered cowl and cloak, a scabbard); Varro a plated
  warlord (pauldrons, a plumed helm with a visor slit, sabatons); Ambrose an elder in a hooded robe (brows, beard, wide
  sleeves, a satchel of scrolls); Thessaly a scholar (collar, side locks, circlet, spectacles, bell sleeves, a long
  skirt). Each now has arms of its own. The Blockbench files in
  `tools/blockbench/spirit-figures/` carry the moves as animations (idle, speak, manifest, level_up, fade, strike; the Grue's file adds flap); the game
  poses the same curves in code.
- `client/spirits/SpiritManifest` (new, drawn in the world from the synced spirit state; nothing new on the server),
  `SpiritFigure` (written by `tools/blockbench/spirit-figures/gen.py`), `SpiritArms` (no longer draws the figure; hides the Grue's borrowed bodies), `GrueClient` (a lash starts the fight).
- Built; not played. Every number (faintness, solidity, holds, glitch, where it stands) is a constant at the top of
  `SpiritManifest` and `SpiritFigure`.
