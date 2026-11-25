---
status: claimed
for: claude
from: claude (PC coordinator thread)
asked: 2026-11-24
claimed_by: mordhau-3-body
pr: 
---
# Mordhau harness 3: body (padding, mail, cuirasses, over-garments, shoulders, arms, hands) (Kingdoms)

**Andy (2026-11-24):** "can you see all the gear available in Mordhau? i'd like the armor pieces modeled for our character's harness". He ticked 84 of 109 rows on https://claude.ai/artifact/KdZEYgBpSEGYBdFB2ZubCk (the picks below are this track's share, copied from that page's store).

**Rules:** our own models of the historical armour each piece is based on, made the way the harness already makes its pieces (kingdoms/tools/gen_heraldry_armour.py, bbmodels in kingdoms/src/main/resources/assets/jaymoskingdoms/bbmodels/heraldry_*.json, HeraldryArmour, HeraldryWear, HeraldryForging, Harness). Mordhau's meshes, textures and names-as-brands belong to Triternion and are not copied. "have" rows need no new model: check the piece is reachable (several "have" pieces have no recipe and are worn only by knights) and add the finish/re-cut the row names. Lore first: rows marked "Lore check" get a (new) Lore.md paragraph for Andy (who makes and wears it). Previews of every new or re-cut piece (worn, front/side/back) to E:/Jaymo Vault Live/Assets/<track>/ for Andy before merging. One PR per track, branch claude/mordhau-3-body, change note changes/kingdoms/claude-mordhau-3-body.md, never version=/MODLOG/codemap. Tuning in constants; player text in lang parts. CI compiles (Andy plays on this PC).

**Andy's notes to carry:** on the kettle hat, "different cuts for different realms"; on the slashed doublet, "you will see guards opt to remove their armor and stay within the harness gear, as it is too much to remove and store" (read in the body track: off duty or in town, guards take off the heavy pieces and keep only the harness's under-layers on; confirm the reading with Andy if it changes much).

## Picked
### Padding and shirts (PADDING)
- **mh-gambeson** Gambeson / aketon (S, have): Gambeson, Gambeson Tunic (P29), Short Gambeson. A quilted padded jacket. Ours: PADDING: have aketon.
- **mh-gambeson-chequer** Chequered gambeson (S, variant): Checkered Gambeson. A gambeson quilted in two colours of cloth. Ours: PADDING: variant of aketon (a two-dye under-sheet).
- **mh-doublet** Arming doublet (S, have): Doublet Padded, Doublet Leather. A 15th c. fitted arming doublet with points to tie armour to. Ours: PADDING: have arming_doublet (leather already). The padded version is the same piece.
- **mh-cloth-tops** Plain shirts, tunics, vests (S, variant): Peasant Shirt, Tunic, Cloth Vest, Workers Vest, Wanderers Vest, Nothing. Linen shirt, wool tunic, sleeveless vest. Ours: PADDING: under-sheet variants of arming_doublet at 0 armour. Or leave them to ordinary clothing; they are not armour.
- **mh-leather-jack** Leather jerkin (S, variant): Leather Armor. A leather jack or jerkin. Ours: PADDING: variant of arming_doublet (leather sheet, sleeveless) or fur_jerkin without the fur. Chest hide_armor is the plate-slot cousin.
- **mh-landsknecht-doublet** Slashed Landsknecht doublet (S, new): Landsknecht Vest, Landsknecht set torso (P26). c.1500 puffed and slashed doublet, a lining colour showing through the cuts. Ours: PADDING: a new under-sheet in two dyes (no model needed). Lore check: mercenary companies. **Andy: you will see guards opt to remove their armor and stay within the harness gear, as it is too much to remove and store**
### Mail and body shirts (MAIL)
- **mh-mail** Mail shirt (S, have): Chainmail, Chainmail Tabard (mail under a tabard), Mail Surcoat. A mail haubergeon. Ours: MAIL: have mail_haubergeon (+ tabard in OVER). (The mail surcoat is in mh-surcoat.)
- **mh-hauberk** Long hauberk (S, variant): Hauberk, Chainmail Coat. An 11th-13th c. mail hauberk to the knees with full sleeves. Ours: MAIL: variant of mail_haubergeon (longer sheet). The skirt part could also come from mail_skirt (WAIST).
- **mh-plated-mail** Mail with plates (S, variant): Plated Mail (P25). Mail with small plates let into it over the chest and back (bakhterets/mail-and-plate). Ours: MAIL: variant of mail_haubergeon (sheet with plate rows).
- **mh-padded-coat** Long padded coat (S, have): Gambeson Coat. A long quilted coat to the knees. Ours: MAIL: have quilted_coat (desert padded coat with a model, dyeable).
- **mh-scale** Scale shirt (M, new): Scale Tunic (P24), Scale Skirt. Scale armour: small overlapping metal scales sewn to a cloth backing. Ours: MAIL: a new under-sheet and shirt icon (2 armour, iron). Lore check: an eastern or ancient people. (The scale skirt is its own row, mh-scale-skirt.)
### Cuirasses (CHEST)
- **mh-cuirass** Plate cuirass (Burgundian, munition) (S, have): Burgundian Cuirass, Footsoldier Cuirass, Veteran Cuirass, Crude Cuirass, Almain Rivet Cuirass (P26), Cuirassier Chest (P22). A breastplate over a pointed plackart with faulds; munition plate is the plain, rough version. Also the datamined Cuirassier Breastplate. Ours: CHEST: have plate_cuirass. Crude/munition = rusted or blackened finish. The Almain rivet's sliding rivets and the cuirassier's heavier plate need no new model.
- **mh-italian** Italian (Milanese) breastplate (S, have): Italian Cuirass (+ Painted), Florentine Cuirass (P22). 15th c. Milanese rounded breastplate, one great swelling plate. Ours: CHEST: have rounded_breastplate. Painted = painted finish.
- **mh-fluted** Fluted (Maximilian) cuirass (S, have): Maximilian Cuirass (+ Painted), Fluted Cuirass (+ Painted), Maximilian set (P26), Dragon Cuirass. Early 16th c. fluted breastplate with a roped neck edge. Ours: CHEST: have fluted_cuirass. Dragon = etched or gilded finish.
- **mh-gothic** Gothic cuirass (S, variant): Gothic Plate, Archduke Cuirass. Late 15th c. German Gothic: a waisted breast with a separate plackart cut in cusped points, rippled fluting fanning out. Ours: CHEST: variant of plate_cuirass (pointed plackart already) with fan fluting and cusped edges, or of fluted_cuirass.
- **mh-kastenbrust** Kastenbrust (M, new): Kasten Brust Chest (P19). The boxy German breastplate of c.1400-1440: a flat-fronted box chest with a sharp angle at the waist, then a long laminated fauld. Ours: CHEST: new model.
- **mh-peascod** Peascod cuirass (M, new): Elizabethan Cuirass (+ Painted). A late 16th c. breastplate with a centre ridge and a belly pulled low into a point (peascod). Ours: CHEST: new model. Lore check: late 16th c. is late beside our other pieces.
- **mh-hussar-cuirass** Lamed hussar breastplate (S, variant): Hussar Cuirass (+ Painted), Hussar set torso (P22). A 17th c. Polish breastplate made of overlapping horizontal lames, often brass-edged. Ours: CHEST: variant of plate_cuirass with lames across the belly and brass edging (gilt_banded finish).
- **mh-muscle** Muscle cuirass (all'antica) (S, variant): Lion Musculata. Renaissance muscle cuirass in the antique style, with a lion mask and a Medusa head. Ours: CHEST: variant of bronze_armor (ancient-style bronze plate). I did not open its model, so I don't know whether it is already muscled; if not, it is M.
- **mh-embossed** Embossed parade cuirass (S, variant): Grotesque Cuirass. A Milanese Renaissance cuirass embossed with monsters and scrolls. Ours: CHEST: variant of etched_cuirass or rounded_breastplate. The look comes from the finish.
- **mh-coat-of-plates** Coat of plates (S, have): Coat of Plates, Coat of Plates Alt, Knightly Coat of Plates, Early Coat of Plates (P25). A 14th c. cloth coat with large plates riveted inside. Ours: CHEST: have coat_of_plates (dyed, COAT material).
- **mh-brigandine** Brigandine and corazzina (S, variant): Brigandine, Corazzina. A 15th c. brigandine: many small plates riveted inside cloth, rows of gilt rivet heads; the Italian corazzina in between, with larger plates. Ours: CHEST: variant of coat_of_plates (the same coat with dense rivet rows). brigandine_spaulders already match it.
- **mh-covered** Cloth-covered plate (S, variant): Burgundian Covered Cuirass, Veteran Clad Cuirass, Covered Black Sallet, Covered Flat Top, Draped / Knights Draped / Campaigners Draped Cuirass. Plate covered in velvet or cloth, or with cloth draped over it. Ours: A new 'covered' finish for PLATE pieces (cloth in the realm's colour), or the OVER giornea worn over the cuirass for the draped looks.
- **mh-jupon** Breastplate over a padded jupon (S, have): Breastplate Over Jupon, Padded Cuirass (+ Painted). A late 14th c. breastplate worn over a quilted coat that shows below it. Ours: CHEST + PADDING: have plate_cuirass or rounded_breastplate over aketon for the body. aketon is an under-sheet with no model, so the padded skirt showing below needs the new quilted skirt (mh-padded-skirt).
- **mh-lamellar** Lamellar (S, have): Plate Lamellar Cuirass, Leather Lamellar Cuirass, Eastern Armor (datamined). Small plates laced together in rows. Ours: CHEST: have laced_lamellar (also the 'lamellar' finish). The leather version = hide finish.
### Over-garments (OVER)
- **mh-tabard** Tabard (S, have): Tabard, Chainmail Tabard. A heraldic tabard over armour. Ours: OVER: have tabard, worn over plate_cuirass (Mordhau's tabard is plate under a heraldic tabard).
- **mh-surcoat** Long surcoat (S, variant): Mail Surcoat, Damaged Surcoat (P24). The long sleeveless 13th c. crusader surcoat to the calves, belted; the damaged one is torn. Ours: OVER: variant of tabard (longer panels). The damaged one is a torn re-cut like torn_cloak.
- **mh-robes** Monk's habit (M, new): Monk Robes (P25), Monk Sleeves. A full-length monastic habit with wide sleeves and a rope belt. Ours: OVER: new model to the feet, dyed. Lore check: which faith keeps monks.
- **mh-kaftan** Kaftan (M, new): Sultan set torso (P24). A long Ottoman robe worn over armour. Ours: OVER: new model, dyed. Same lore check as the turban.
- **mh-motley** Jester's motley (M, new): Jester set torso and legs (P23). A parti-coloured coat with dagged edges and bells. Ours: OVER: new model, two dyes.
- **mh-sash** Sash (S, new): Archduke Sash (in place of a skirt). A cloth sash tied round the waist or across the body, a sign of rank or side. Ours: OVER: a small new model, dyed in the realm's colour.
### Shoulders (SHOULDERS)
- **mh-pauldrons** Large pauldrons (M, new): Cuirassier pauldrons (patch notes, named only). 16th-17th c. big pauldrons whose lames wrap over the breast and back. Ours: SHOULDERS: new model; plate_spaulders cover the shoulder only.
- **mh-ailettes** Ailettes (M, new): Bishop's Ailettes (patch notes). Late 13th-14th c. flat boards tied upright on the shoulders, painted with the wearer's arms. Ours: SHOULDERS: new model; it could take the realm's ARMS overlay as the tabard does.
### Arms (ARMS)
- **mh-arm-harness** Arm harness (S, have): Cuirassier arms (patch notes); the arm pieces of every plate set. Upper and lower cannons with a couter. Ours: ARMS: have plate_arm_harness.
### Hands (HANDS)
- **mh-mitten-gauntlet** Mitten gauntlet (S, variant): Italian gauntlets (patch notes). The 15th c. Italian mitten gauntlet: one set of lames over all four fingers, a long flared cuff. Ours: HANDS: variant of plate_gauntlets (fingers merged into lames).

## Notes
- 2026-11-25 07:33 claude (PC coordinator thread, worker)'s claim had no sign of work for 30 min: handed back as open.
- 2026-11-25 4:12 PM claude (T3 thread, Sonnet 5.5) claimed both Mordhau tracks (3 then 4, one PR each; 4 stacks on 3). Reading of the slashed-doublet note: off duty a guard's heavy places come off and the under-layers stay on (padding, mail, hose); recorded in the agent note.
