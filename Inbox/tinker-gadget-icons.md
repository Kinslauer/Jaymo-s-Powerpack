---
status: claimed
for: hermes-modeller
from: coordinator
asked: 2026-11-15
claimed_by: tinker-gadget-icons
pr: 
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWUTuunHRPHZ4DW2GXz6y1Qf
---

# Redraw the Tinkerer's 19 gadget icons

**Andy's words:** "Merge and create a writeoff for the art" (after PR #1606, the Tinkerer's mimic gadgets, was built with
placeholder icons drawn by a script).

**What's wanted:** 19 proper item icons for the Tinkerer's mimic gadgets. They should look hand-drawn, in the vanilla
style, so each one reads at a glance in the hotbar and on the Tinkerer's Notebook screen. The current icons came from
`tools/gen_tinker_mimic.py` and are flat and samey. Sonic Horn and Parry Gauntlet are the weakest. Current sheet:
`/mnt/project-files/tinker-gadgets/gadget-icons.png`.

**Where to start:** `kingdoms/src/main/java/com/jaymoskingdoms/tinker/Mimic.java` has the list, and
`kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/tinker_mimic.json` has each gadget's name and tooltip.
No code change is needed. Replace the PNGs in place, keeping the same filenames.

## Rules
- 16x16 PNG, transparent background, vanilla palette and shading (light from the top left, a dark outline on the
  bottom-right edges). These are flat item icons only, with no 3D or GeckoLib models. Lift Rig and Jaw Rake use
  `item/handheld`, so draw those diagonally from bottom-left to top-right, like a vanilla tool. The other 17 use
  `item/generated`, so draw them as objects held in the hand.
- Original art only. Don't copy another mod's textures.
- Lore (PR #1606, "What a tinker copies"): a Tinkerer watches a gift or a creature's trick until they can draw it,
  then builds the drawing in **brass, spring and whatever the thing burns**. Every gadget should look like a workshop
  object (brass/copper/iron, rivets, springs, glass) with one hint of what it copies. The Mad Tinkerer's work is
  scrappy and clever, not shiny or magical.
- Leave `tools/gen_tinker_mimic.py` alone (or delete its texture step, but don't rerun it over the new art).

## The 19 icons (file = `textures/item/<id>.png`)
| id | Name | Copies | What it does | Suggested look |
|---|---|---|---|---|
| lift_rig | Lift Rig | Gravity power, shulkers | hold on a creature: it floats where you point | brass rod with a pale purple shulker-shell lens and two little floating pellets |
| gust_bellows | Gust Bellows | Wind power, breezes | cone of wind, or launch yourself | small leather bellows with a brass nozzle and a pale swirl coming out |
| grapple_spinner | Grapple Spinner | Web power | line to a block, pulls you in | hand reel of string with a three-prong iron hook |
| rewind_watch | Rewind Watch | Chrono power | back 3 seconds | gold pocket watch, open lid, hands drawn running backwards (a little arrow) |
| frost_projector | Frost Projector | Cryo power | hold: cone of cold | brass barrel with a frosted glass tank of blue, ice crystals at the muzzle |
| smother_pump | Smother Pump | Smoke power | blinds and chokes | hand pump with a soot-black canister and a grey puff |
| sonic_horn | Sonic Horn | Resonance power, wardens | sound blast | curled brass horn with a teal warden-like sculk ring in the bell |
| parry_gauntlet | Parry Gauntlet | Darktide power | parry window | riveted iron gauntlet with a dark steel bracer plate on the forearm |
| chain_hook | Chain Hook | Arsenal power | yanks a creature in | coiled iron chain ending in a heavy hook |
| growth_band | Growth Band | Xenoform power | grow bigger for 30 s | copper arm band set with a green alien-looking glowing stone |
| ki_blaster | Ki Blaster | Z Fighter power | bolt of stored force | stubby brass hand cannon with a golden-yellow glowing core |
| blink_coil | Blink Coil | endermen | short teleport | copper wire coil wound round an ender-pearl-green bulb, purple sparks |
| climbing_claws | Climbing Claws | spiders | climb walls | leather hand strap with three curved iron hooks (spider-leg feel) |
| flame_spitter | Flame Spitter | blazes | 3 small fireballs | short brass nozzle gun with a blaze-rod-yellow chamber, a small flame at the tip |
| bellows_cannon | Bellows Cannon | ghasts | one bursting fireball | wide-mouthed iron tube on a bellows, a fire charge sitting in the mouth |
| beam_lens | Beam Lens | guardians | hold 2 s, beam burns | prismarine-framed lens on a brass handle, a guardian-eye pupil in the glass |
| jaw_rake | Jaw Rake | evokers | line of fangs from the ground | rake-like tool whose teeth are bone fangs |
| hissing_charge | Hissing Charge | creepers | thrown, bursts (stacks of 8) | round green-mottled canister with a lit fuse; small, like a grenade, not a tool |
| potion_sling | Potion Sling | witches | throws your off-hand potion far | leather sling with a wooden fork, a purple potion bottle in the pouch |

## Notes
- Check in game, or render them side by side: each icon must be told apart from the others at hotbar size.
- Open the PR against master with a change note `changes/kingdoms/<branch>.md` (art only, no version bump), then set
  the task to in-review.
