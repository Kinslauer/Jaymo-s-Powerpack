# Mordhau harness 3: body previews and row table

Each picture is one piece worn on a grey figure, seen (left to right) from the **front**, the **side**, a **three-quarter** view, the **back**
and a second three-quarter view. Drawn with `tools/bbmodel_preview.py` from the baked models and sheets, the way the game draws them; not
screenshots from the game. Cloth is shown tinted in the colour an undyed one shows (the game tints it with the piece's dye); `_gilded`,
`_gilt_banded` and `_covered` are the same piece in that finish; `_over_plate` is a garment worn over the cuirass and spaulders.

Nothing here has been played. Java compiles in CI only (Andy plays on this PC, so no Gradle here); the generator (`kingdoms/tools/gen_heraldry_body.py`) and
these pictures were run here.

| Pick (row) | What it is now | Picture |
|---|---|---|
| mh-gambeson (have) | `aketon`: recipe `aketon.json` (wool and string), dyed with any dye; Mordhau's Short Gambeson is the same piece. No new model. | (existing) |
| mh-gambeson-chequer | new `chequered_gambeson` (PADDING, 1 armour): the aketon's quilting in squares of two colours; the dye gives the first, the dye it is made with the second | `chequered_gambeson` |
| mh-doublet (have) | `arming_doublet`: recipe `arming_doublet.json`, the shepherd sells it, guards are kitted with it; leather already. No new model. | (existing) |
| mh-cloth-tops | new `linen_shirt`, `wool_tunic` (with a short skirt) and `cloth_vest` (PADDING, no armour, dyed). "Nothing" is no piece. | `linen_shirt`, `wool_tunic`, `cloth_vest` |
| mh-leather-jack | new `leather_jack` (PADDING, 1 armour, mended with leather), sleeveless, laced down the front | `leather_jack` |
| mh-landsknecht-doublet | new `landsknecht_doublet` (PADDING, 1 armour): slits cut through the cloth with the second colour showing. Guards: see "Guards" below. Lore (new): "The company's doublet" | `landsknecht_doublet` |
| mh-mail (have) | `mail_haubergeon`: forged from iron at the smith's anvil (already). The tabard over it: row mh-tabard. | (existing) |
| mh-hauberk | new `mail_hauberk` (MAIL, 3 armour): full sleeves on the sheet, the mail skirt to the knee a model hung on the legs; forged from iron | `mail_hauberk` |
| mh-plated-mail | new `plated_mail` (MAIL, 3 armour): the haubergeon with plates let into the chest and back; forged from iron | `plated_mail` |
| mh-padded-coat (have) | `quilted_coat`: recipe `quilted_coat.json`. It had no model and was drawn as a mail haubergeon; it now has its own quilted sheet and a long skirt, dyeable | `quilted_coat` |
| mh-scale | new `scale_shirt` (MAIL, 2 armour): overlapping scales, forged from iron. Lore (new): "Scale and the old age" | `scale_shirt` |
| mh-cuirass (have) | `plate_cuirass`: forged (already). Munition/crude = the blackened finish (already) and a new russeted (browned) finish at the anvil; rusted comes with wear. Can be covered. | (existing) |
| mh-italian (have) | `rounded_breastplate`: was worn only by knights, now forged (8 steel); painted finish added at the anvil. Can be covered. | (existing) |
| mh-fluted (have) | `fluted_cuirass`: was worn only by knights, now forged (8 steel); painted and etched ("dragon") finishes added, gilded for nobles. Can be covered. | (existing) |
| mh-gothic | new `gothic_cuirass`: waisted, a separate cusped plackart, fan fluting; forged; etched finish. Can be covered. | `gothic_cuirass`, `_gilded`, `_covered` |
| mh-kastenbrust | new `kastenbrust`: flat box chest, sharp waist, long laminated fauld; forged. Can be covered. | `kastenbrust`, `_gilded`, `_covered` |
| mh-peascod | new `peascod_cuirass`: centre ridge, belly drawn down to a point; forged; painted finish. Can be covered. Lore (new): "The peascod and the late plate" | `peascod_cuirass`, `_gilded`, `_covered` |
| mh-hussar-cuirass | new `hussar_cuirass`: overlapping horizontal lames, brass edging; forged, with the gilt-banded and painted finishes | `hussar_cuirass`, `hussar_cuirass_gilt_banded` |
| mh-muscle | new `muscle_cuirass` (its own muscled model, not a re-skin of bronze armour): breasts, abs, lion mask, leather strap skirt; forged, with a bronze finish | `muscle_cuirass`, `_gilded` |
| mh-embossed | new `embossed_cuirass`: the rounded breastplate with scrollwork and a grotesque face in relief; forged, painted and etched finishes | `embossed_cuirass`, `_gilded` |
| mh-coat-of-plates (have) | `coat_of_plates` had no recipe (realm armourers and knights only): new crafting recipe (wool, iron, string) | (existing) |
| mh-brigandine | new `brigandine_coat`: the coat of plates' cut with the rivet heads close set, row on row; crafting recipe (wool, iron, nuggets); dyed | `brigandine_coat` |
| mh-covered | new **covered** finish at the anvil: wool over the plate, its colour the wool's (2 wool). On the cuirasses (plain, rounded, fluted, Gothic, kastenbrust, peascod), the sallets and the flat-topped helm. Draped looks: the giornea and the new surcoat over a cuirass. Andy's two flat-top looks: the Covered flat top and the Royal flat top (flat-topped helm with the helm crown). | `plate_cuirass_covered`, `rounded_breastplate_covered`, `fluted_cuirass_covered`, `sallet_covered`, `kettle_sallet_covered`, `archers_sallet_covered`, `flat_top_helm_covered`, `royal_flat_top` |
| mh-jupon (have) | a breastplate over the aketon, and the quilted coat's skirt shows below the tassets (`quilted_coat` under a cuirass). The padded skirt alone is track 4's `mh-padded-skirt`. | `quilted_coat` |
| mh-lamellar (have) | `laced_lamellar` was worn only by knights, now forged (7 steel) with a hide finish (leather lamellar) | (existing) |
| mh-tabard (have) | `tabard`: recipe `tabard.json` (wool), sold in realms; worn over any cuirass | (existing) |
| mh-surcoat | new `surcoat` (dyed, to the calves, belted; the realm's shepherd sells it in the realm's colour, wool recipe) and `torn_surcoat` (the torn re-cut; found, not crafted). | `surcoat`, `surcoat_over_plate`, `torn_surcoat` |
| mh-robes | new `monks_robe`: full length, wide sleeves, a rope at the waist; dyed. Lore (new): "The habit and the rope" | `monks_robe`, `monks_robe_over_plate` |
| mh-kaftan | new `kaftan`: long open robe, gilt braid and cuffs, dyed. Lore (new): "The kaftan" | `kaftan`, `kaftan_over_plate` |
| mh-motley | new `jesters_motley`: two colours split down the middle, a dagged hem with a bell on each; the second colour is the dye it is made with | `jesters_motley` |
| mh-sash | new `sash`: a band across the chest to a knot at the hip, dyed | `sash`, `sash_over_plate` |
| mh-pauldrons | new `pauldrons` (SHOULDERS, 2 armour): deep lames over the shoulder and lames wrapping over the breast and back; forged | `pauldrons`, `pauldrons_gilded` |
| mh-ailettes | new `ailettes`: a flat board upright on each shoulder bearing the realm's arms as the tabard does; the realm's armourers sell them with the arms laid on, anyone can craft a plain one | `ailettes` |
| mh-arm-harness (have) | `plate_arm_harness`: forged (already) | (existing) |
| mh-mitten-gauntlet | new `mitten_gauntlets` (HANDS): a long flared cuff and one set of lames over all four fingers; forged | `mitten_gauntlets`, `_gilded` |

## Guards (Andy's note on the slashed doublet)

Andy: "you will see guards opt to remove their armor and stay within the harness gear, as it is too much to remove and store". The task asked
for a reading and to confirm it with Andy if it changes much. **Reading taken:** when a guard or knight puts his armour by (off duty, in a
town), the heavy places of the harness (shoulders, arms, hands, thighs, shins, neck, waist, hood, crest, over-garment) come off with it, and the
under-layers stay on: the padding and the mail (`Harness.UNDER_LAYERS`). Before this, everything in the harness came off with the armour. Tell me
if "the harness gear" should mean the whole harness instead (then guards would keep all of it on and only the helm, chest piece and boots go).

## Not done / not checked

- Not played; Java not compiled here (CI compiles).
- The slashed doublet's sleeves are slashed but not puffed (the under-armour layer is flat on the arm).
- Covered plate is coloured by the wool the smith carries; guards and shops do not sell it, and there is no re-dye recipe for it.
