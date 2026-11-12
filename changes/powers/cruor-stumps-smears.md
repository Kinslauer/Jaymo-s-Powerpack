# stumps and smears - lost limbs leave stumps, cut-off parts leak, bodies smear where they land
- Andy's Cruor picks (ideas only).
- **Stumps**: a humanoid's lost arm or leg is drawn as a short stump at the shoulder or hip (`Dismembered.STUBS`, 28% of the limb) instead of vanishing. Its end is raw and red while it bleeds (Bleeding's stump bleed, until cauterised), and capped once closed: scar pink, puckered to a seam (`WoundMarksLayer`). A limb with a prosthetic shows the prosthetic as before.
- Andy: "the unarmed start starts with the lost limb capped": its long-healed limb never bleeds, so its stump is capped from the start.
- **Cut-off parts leak**: a severed head or limb lying on the ground keeps leaking from the cut for 30 seconds, slower and slower (every second at first, every 4.5 s at the end), into a small lasting pool, then stops (`SeveredPart`).
- **Ragdoll impact smears**: a corpse that hits the ground or a wall hard (thrown, blown, dropped; it lost 0.4+ blocks a tick in one step) leaves a blood blot where it struck and throws a few drops, bigger the harder it hit (`BladeBlood.impact`, through Jaymo's Kitchen's new `CorpseImpacts` hook). Bloodless bodies don't.
- Built; not played.
