---
status: in-review
for: claude
from: claude (PC coordinator thread)
asked: 2026-11-24
claimed_by: 
pr: 2178
---
# Mordhau harness 2: hoods, hats, crests and neck (Kingdoms)

**Andy (2026-11-24):** "can you see all the gear available in Mordhau? i'd like the armor pieces modeled for our character's harness". He ticked 84 of 109 rows on https://claude.ai/artifact/KdZEYgBpSEGYBdFB2ZubCk (the picks below are this track's share, copied from that page's store).

**Rules:** our own models of the historical armour each piece is based on, made the way the harness already makes its pieces (kingdoms/tools/gen_heraldry_armour.py, bbmodels in kingdoms/src/main/resources/assets/jaymoskingdoms/bbmodels/heraldry_*.json, HeraldryArmour, HeraldryWear, HeraldryForging, Harness). Mordhau's meshes, textures and names-as-brands belong to Triternion and are not copied. "have" rows need no new model: check the piece is reachable (several "have" pieces have no recipe and are worn only by knights) and add the finish/re-cut the row names. Lore first: rows marked "Lore check" get a (new) Lore.md paragraph for Andy (who makes and wears it). Previews of every new or re-cut piece (worn, front/side/back) to E:/Jaymo Vault Live/Assets/<track>/ for Andy before merging. One PR per track, branch claude/mordhau-2-hoods-crests, change note changes/kingdoms/claude-mordhau-2-hoods-crests.md, never version=/MODLOG/codemap. Tuning in constants; player text in lang parts. CI compiles (Andy plays on this PC).

**Andy's notes to carry:** on the kettle hat, "different cuts for different realms"; on the slashed doublet, "you will see guards opt to remove their armor and stay within the harness gear, as it is too much to remove and store" (read in the body track: off duty or in town, guards take off the heavy pieces and keep only the harness's under-layers on; confirm the reading with Andy if it changes much).

## Picked
### Hoods and coifs (HOOD)
- **mh-hood** Cloth hood (S, have): Hood, Wanderers Hood, Workers Hood. A medieval hood with a point (liripipe) and a short shoulder cape. Ours: HOOD: have hood (torn_hood for ragged).
- **mh-hood-cuts** Tight hood, monk's cowl, bishop's mantle (S, variant): Tight Hood, Monk Hood (P25), Bishops Mantle. A close-fitting hood; a monastic cowl that is deep and has no point; a cleric's hood with a wider shoulder cape (mozzetta). Ours: HOOD: variants of hood (re-cut of the same cubes).
- **mh-executioner** Eyeholed hood (S, variant): Executioner Hood (Halloween 2020 only). A black hood pulled over the face with eyeholes (popular image, not strictly historical). Ours: HOOD: variant of hood that closes over the face. Lore check needed (who wears it, e.g. the Banished or a realm's headsman).
- **mh-coif** Mail coif (S, have): Chain Coif. An 11th-13th c. mail hood falling to the shoulders. Ours: HOOD: have mail_coif.
- **mh-coif-cuts** Lowered coif and coif with ventail (S, variant): Maille Hood Lowered, Maille Hood Spiked Lowered, Crusader Mail Coif (+ Mouth Piece, Square Cut, Square Cut & Mouth Piece). The coif pushed back off the head to lie round the neck; the 12th-13th c. coif with a laced flap (ventail) over the mouth, round or square-cut face opening. Ours: HOOD: variants of mail_coif (cube re-cuts). The spiked one is an odd Mordhau touch; skip it or give it to bandits.
- **mh-arming-cap** Arming cap (padded coif) (M, new): Padded Coif. A quilted linen cap tied under the chin, worn under a helm or alone. Ours: HOOD: new small model, 1 armour at most, dyed like the aketon.
- **mh-soft-caps** Soft caps (M, new): Peasant Cap, Bag Hat, Archers Cap (+ Feather). A linen coif-cap, a floppy sack cap, and a 15th c. felt cap with an upturned brim and peak. Ours: HOOD, if a hat goes there (no helm under it). New models (one per cut, or one with re-cuts). Feather = crest_plume only if CREST stops needing a HEAD item.
- **mh-brimmed-hats** Brimmed hats (M, new): Peasant Hat, Hunter's Hat, Scout's Hat, Fisherman's Hat (P23). A straw or felt hat with a wide brim; the hunter's hat with a pinned brim and feather. Ours: HOOD (no helm under it): new model. Frontier already has settler_hat_helmet in vanilla HEAD, which is a different system; check it isn't the same look before modelling.
- **mh-landsknecht-hat** Landsknecht bonnet (M, new): Landsknecht Hat (+ Feather). c.1500-1530 wide slashed beret worn tilted, heaped with plumes. Ours: HOOD: new model, dyed with a slashed lining colour. The plumes are part of the model.
- **mh-fur-hat** Fur or goatskin hat (S, variant): Goatskin Hat (P23). A fur hat. Ours: HOOD: variant of fur_hood (a cap only, without the shoulder pelts).
- **mh-turban** Turban (M, new): Turban, Turban with Veil (P25), Sultan set head (P24). Cloth wound round the head; the veiled version has a face cloth. Turbans were also wound round helmets. Ours: HOOD: new model, dyed. Lore check needed: whose people wear it (desert lands already have quilted_coat).
- **mh-jester** Jester's hood (M, new): Jester set head (P23). A parti-coloured hood with ass's ears or points and bells. Ours: HOOD: new model, two dye colours. Lore check needed: a court fool in a royal hall.
- **mh-wreath** Laurel or leaf wreath (M, new): Laurel, Leaf Crown (tournament-win reward). A classical victor's wreath, in leaves or in metal. Ours: HOOD (worn bare-headed; CREST needs a HEAD item, so it can't go there): new small model. A good tournament prize if Kingdoms has tourneys.
### Crests (CREST)
- **mh-plume** Plume (S, have): Every Mordhau '+ Plume' helm: Armet Plume, Veterans Barbute/Helm Plume, German Sallet Plume, Flat/Pig Face Bascinet Plume. Ostrich or other feathers in a holder on the helm. Ours: CREST: have crest_plume.
- **mh-orle** Orle and torse (S, have): Covered Flat Top Orle, Enclosed Flat Top Orle, Vanguard's Kettle Orle and Buffet Orle. A padded roll of cloth round the helm's crown. Ours: CREST: have torse (twisted two-cloth wreath). A plain padded orle is only a texture variant.
- **mh-crown** Crown on the helm (M, new): Royal Flat Top, Royal Helmet, Champion's Barbute (crowned heavy barbute), the crown in the Patch notes. A gold circlet with points or leaves worn round a helm. Ours: CREST: new model (circlet). It would fit the existing royal rule (gilded only for royals/lords, HeraldryWear.mayWear) and the Crowns system. Lore check: what it means next to the realm's crown item.
- **mh-antlers** Antlers (S, variant): Archduke Sallet with antlers, Archduke Heavy sallet with antlers. Stag antlers fixed to a parade sallet. Ours: CREST: re-cut of crest_horns into branching antlers. There is also crest_stag (the whole beast), which may already be enough.
- **mh-mantling** Mantling (M, new): Greathelm Cloak, Greathelm Cloak Paint (my reading: the cloth hung from the crown of the great helm, the lambrequin). Ours: CREST: new cloth model, dyed in the realm's colours, worn under the torse.
### Bevors and gorgets (NECK)
- **mh-bevor** Bevor and buffe (S, have): Sallet Bevor, Painted Sallet Bevor, Burgonet Buffet, German Sallet Buffet, Vanguard's Kettle Buffet (+ Orle, Straps), Kettle Sallet Bevored. A plate guarding the chin and throat, strapped behind, or a buffe fixed to the helm. Ours: NECK: have bevor, worn with burgonet or kettle_hat (not with our sallet, which already has one).
- **mh-wrapper** Armet wrapper (S, variant): Italian Armet Wrapper / Alt Wrapper. A 15th c. reinforcing plate strapped round the lower face and chin of an armet. Ours: NECK: variant of bevor (a lower, rounder plate cut to sit over the armet's cheeks).
- **mh-gorget** Gorget (S, have): Mordhau's gorget slot (neither list itemises it). A lamed steel collar. Ours: NECK: have gorget. Mail collar = standard_of_mail.

## Notes
- 2026-11-24 22:35 claude (PC coordinator thread, worker)'s claim had no sign of work for 30 min: handed back as open.

- 2026-11-25: Andy approved the previews and all five lore entries for #2178.
