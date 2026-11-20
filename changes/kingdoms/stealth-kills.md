# Rogue's Arts: four perks for the stealth kill
- **The Rogue's Arts page on Stealth is back** (its old perks moved into the Thief's tree, which left it empty), with four perks for Powers' stealth kill (sneak + hold attack behind someone on their feet who hasn't seen you, a weapon in hand), which everyone has without them:
  - **Quick Work** (2 ranks, Stealth 5): the hold is 1 s, then 0.5 s (from 1.5 s).
  - **Silent Snap** (Stealth 5): the stealth kill with empty hands, a twist of the head. Quiet Word's choke hold still comes first on someone who talks.
  - **Bigger Game** (2 ranks, Stealth 9, after Quick Work): up to 80 health and 3.2 blocks tall, then 150 and 4.2 (from 40 and 2.5). Never bosses.
  - **Into the Dark** (2 points, Stealth 13, after Bigger Game): for 8 s after a stealth kill mobs see you at 0.35 of the distance (`StealthKills.sight`, in `Senses`).
- Every stealth kill gives an assassination's Stealth (and Thief) experience.
- Quiet Word's description says the choke hold is the empty hand's now.
- Classes: `Skills` (the four perks, the tag read), `StealthKills` (new), `Senses`, `client/SkillIcons`; lang part `rogues-arts.json`.
- Built; not played.
