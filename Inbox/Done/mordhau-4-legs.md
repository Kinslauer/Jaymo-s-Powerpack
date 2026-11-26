---
status: done
for: claude
from: claude (PC coordinator thread)
asked: 2026-11-24
claimed_by: mordhau-4-legs
pr: 2187
---
# Mordhau harness 4: waist, legs and feet (and a new HOSE place) (Kingdoms)

**Andy (2026-11-24):** "can you see all the gear available in Mordhau? i'd like the armor pieces modeled for our character's harness". He ticked 84 of 109 rows on https://claude.ai/artifact/KdZEYgBpSEGYBdFB2ZubCk (the picks below are this track's share, copied from that page's store).

**Rules:** our own models of the historical armour each piece is based on, made the way the harness already makes its pieces (kingdoms/tools/gen_heraldry_armour.py, bbmodels in kingdoms/src/main/resources/assets/jaymoskingdoms/bbmodels/heraldry_*.json, HeraldryArmour, HeraldryWear, HeraldryForging, Harness). Mordhau's meshes, textures and names-as-brands belong to Triternion and are not copied. "have" rows need no new model: check the piece is reachable (several "have" pieces have no recipe and are worn only by knights) and add the finish/re-cut the row names. Lore first: rows marked "Lore check" get a (new) Lore.md paragraph for Andy (who makes and wears it). Previews of every new or re-cut piece (worn, front/side/back) to E:/Jaymo Vault Live/Assets/<track>/ for Andy before merging. One PR per track, branch claude/mordhau-4-legs, change note changes/kingdoms/claude-mordhau-4-legs.md, never version=/MODLOG/codemap. Tuning in constants; player text in lang parts. CI compiles (Andy plays on this PC).

**Andy's notes to carry:** on the kettle hat, "different cuts for different realms"; on the slashed doublet, "you will see guards opt to remove their armor and stay within the harness gear, as it is too much to remove and store" (read in the body track: off duty or in town, guards take off the heavy pieces and keep only the harness's under-layers on; confirm the reading with Andy if it changes much).

## Picked
### Faulds, tassets and skirts (WAIST)
- **mh-long-tassets** Long tassets to the knee (S, variant): Cuirassier Legs (P22, tier 2), Elizabethan Leggings' long tassets. Late 16th-17th c. lamed tassets down to the knee. Ours: WAIST: variant of faulds_and_tassets (longer lames).
- **mh-plate-skirts** Coat-of-plates and lamellar skirts (S, variant): Coat of Plates Skirt (+ Alt), Lamellar Skirt, Leather Lamellar Skirt. A cloth skirt with plates riveted inside, or rows of laced lamellae. Ours: WAIST: variants of leather_tassets (cloth-and-rivet or lamellar texture on the same cubes).
- **mh-padded-skirt** Quilted skirt (M, new): Gambeson Skirt (No Belt), Longskirt variants. The long quilted skirt of an aketon worn alone at the hips. Covers Mordhau's Short Gambeson too. Ours: WAIST: new cloth model (1 armour, string), dyed like the aketon.
- **mh-cloth-skirt** Cloth skirt and base (M, new): Nordic Skirt, Wanderers/Workers Skirt, Butcher's Skirt (apron, P25), Grotesque Skirt, Lion Skirt, Dragon Embroidered Skirt. A plain belted cloth skirt; and the 16th c. pleated 'base' worn over leg harness. Ours: WAIST: new cloth model, dyed (0 armour). One model with length re-cuts covers these.
- **mh-scale-skirt** Scale skirt (S, variant): Scale Skirt. Ours: WAIST: variant of mail_skirt with the scale texture from the scale shirt.
### Thighs (THIGHS)
- **mh-fluted-legs** Fluted leg harness (S, variant): Fluted Legs. Maximilian-style fluted cuisses and poleyns. Ours: THIGHS: variant of plate_cuisses with flutes.
- **mh-brigandine-cuisses** Brigandine cuisses (S, variant): Brigandine Greaves (Mordhau name). Cloth thigh defences with plates riveted inside. Ours: THIGHS: variant of padded_cuisses (cloth and rivets, iron), as brigandine_spaulders reuses the plate_spaulders cubes.
- **mh-leather-cuisses** Leather leg pads (S, variant): Leather Pads set, leather pads over hosen (P24 and Hotfix 1). Boiled-leather thigh and knee defences. Ours: THIGHS: variant of padded_cuisses (leather texture, mended with leather).
- **mh-chausses** Mail chausses (M, new): Chainmail Hosen, Padded Chainmail Hosen, chain boots (P24 Hotfix 1). 12th-13th c. mail leggings covering thigh to foot, laced behind, over padded hose. Ours: THIGHS: new mail model on the leg bones (iron), possibly also on the boot bones. A Kitchen body part (legs) already exists for THIGHS.
### Shins (SHINS)
- **mh-schynbald** Schynbald (front shin plate) (S, variant): Calf Guard Hosen. A 13th-14th c. plate strapped over the front of the shin only, over hose or mail. Ours: SHINS: variant of plate_greaves (front half only).
- **mh-leg-wraps** Leg wraps (S, new): Leg Wraps (P25). Cloth bands wound round the calf (early medieval winingas). Ours: SHINS: a small new cloth model at 0 armour, dyed. This may be better left as clothing.
### Feet (vanilla FEET)
- **mh-scale-sabatons** Scale sabatons (S, variant): Scale sabatons (patch notes). Ours: FEET: variant of plate_sabatons with the scale texture.
- **mh-mail-shoes** Mail shoes (M, new): Mail shoes (patch notes). The mail foot of chausses, worn alone. Ours: FEET: new model (vanilla boots slot, iron). It could share the mail chausses model's boot bones.
### Legs (no harness place)
- **mh-hose** Hose and breeches (L, new): Pants, Baggy Pants, Linnen Hosen, Wanderers Pants/Shorts, Workers Shorts, Hunters Hosen, Riders Hosen, Joined Hosen, Light Cavalry Joined Hosen. Medieval braies and 15th c. joined hose. Ours: No place for them: LEGS takes no heraldry piece and none of the twelve places is clothing on the legs. It needs a new harness place (a HOSE place on the end of Harness.Slot) or a vanilla LEGS item.
- **mh-landsknecht-pants** Slashed Landsknecht hose (L, new): Landsknecht Pants, Landsknecht set legs (P26). Puffed and slashed hose, often one leg different from the other. Ours: Same as hose: needs a new place. Pairs with the slashed doublet (PADDING).
- 2026-11-25 4:12 PM claude (T3 thread, Sonnet 5.5) claimed both Mordhau tracks (3 then 4, one PR each; 4 stacks on 3). Reading of the slashed-doublet note: off duty a guard's heavy places come off and the under-layers stay on (padding, mail, hose); recorded in the agent note.
- 2026-11-25 5:10 PM claude (T3 thread): draft PR #2186 (stacked on #2185); previews and row table in Assets/mordhau-4-legs/ (README); waiting on Andy's preview approval and CI. Track 3's CI (#2185) is green: Kingdoms, Powers, Kitchen builds and the pack start check.
- 2026-11-25 5:21 PM PR #2186 closed (no CI: not based on master); replaced by draft #2187 from the same branch to master, which also holds #2185's commits until that merges. Previews regenerated with a true front/side/back.
- 2026-11-25 5:40 PM claude (T3 thread): merged the body track's puffed slashed doublet into this branch (5167428419).

## Notes
- 2026-11-25 19:34 PR #2187 is merged into master; moved to Inbox/Done.
