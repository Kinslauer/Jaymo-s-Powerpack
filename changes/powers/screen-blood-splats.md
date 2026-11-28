# Smoother blood on your screen
- **Blood on your screen** (Andy: "also improve the blood splatter on screen"): the spatters were stepped circles drawn a gui pixel row at a time with square runs down to the hotbar, nearly opaque (78%), anywhere on the screen including over the crosshair. Now each is one of five smooth splat pictures (soft edges, flung droplets and runs, `textures/gui/blood/screen_splat_*.png`), the blood's own colour a little darkened, at most 70%, popping in, running a short way down and fading in about 3-4 s.
- They land on the side they came from: a big blow close by on the side of the screen it happened on, and (new) a blow that hurts you on the side it came from, more and bigger the harder it was. None ever lies over the crosshair (or the line under it) or the hotbar; at most 9 up at once (times the gore level).
- A blood drop flying within 1.1 blocks of your eyes in first person is no longer drawn there (its 16-pixel picture blown up to a hand's width showed as red blocks across the bottom of the view); one flying straight at your eyes lands on the screen as a splat instead, at most one every 4 ticks. It still leaves its stain where it falls.
- The hurt flash's red sides now fade a pixel at a time like its top and bottom (they were 8 visible steps).
- Respects the blood settings: Off shows none, the gore level sets how many and how big, streamer safe draws grey. First person only; nothing in creative or spectator; wiped when you die or go down.
- Classes: `BloodScreen` (rewritten), `BloodDrop`, `HurtCauseHud`, `PainHud`.
- Built; not played.
