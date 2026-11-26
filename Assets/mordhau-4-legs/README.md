# Mordhau harness 4: waist, legs and feet, and the HOSE place: previews and row table

Each picture is one piece worn on a grey figure, seen (left to right) from the **front**, the **side**, the **back**, then **three-quarter
front** and **three-quarter back**. Drawn with `tools/bbmodel_preview.py` from the baked models and sheets, the way the game draws them; not screenshots
from the game. Cloth is shown tinted in the colour an undyed one shows (the game tints it with the piece's dye); `_gilded` is the same
piece in that finish; `_over_leg_harness` is a skirt or tassets worn over cuisses, greaves and sabatons. The figure's legs are drawn short,
as in every harness preview.

Nothing here has been played. Java compiles in CI only (Andy plays on this PC, so no Gradle here); the generator
(`kingdoms/tools/gen_heraldry_legs.py`) and these pictures were run here. The PR (#2187) is based on master so CI runs; it contains track 3's commits (PR #2185) until that merges, so merge #2185 first. #2186, the first try (based on #2185's branch), was closed: no CI runs on a PR that is not to master.

| Pick (row) | What it is now | Picture |
|---|---|---|
| mh-long-tassets | new `long_tassets` (WAIST, 2 armour): hoops round the hips and four lames to the knee before each thigh, a plate down each side; forged from steel | `long_tassets`, `_gilded`, `long_tassets_over_leg_harness` |
| mh-plate-skirts | new `coat_of_plates_skirt` (cloth with riveted plates, dyed; crafted), `lamellar_skirt` (laced iron plates; forged from iron), `leather_lamellar_skirt` (crafted from leather) on the leather tassets' cut | `coat_of_plates_skirt`, `lamellar_skirt`, `leather_lamellar_skirt` |
| mh-padded-skirt | new `padded_skirt` (WAIST, 1 armour): the long quilted skirt of an aketon, dyed. Covers Mordhau's Short Gambeson skirt too. This is also the skirt the body track's jupon row names. | `padded_skirt` |
| mh-cloth-skirt | one cut in six lengths and kinds, all dyed, 0 armour: `cloth_skirt` (knee), `short_skirt` (workers' and wanderers'), `long_skirt` (Nordic), `apron` (butcher's, front only), `pleated_base` (the bell worn over leg harness), `embroidered_skirt` (hem and waist in a second colour, for the Grotesque, Lion and Dragon embroidered skirts) | `cloth_skirt`, `short_skirt`, `long_skirt`, `apron`, `pleated_base`, `pleated_base_over_leg_harness`, `embroidered_skirt` |
| mh-scale-skirt | new `scale_skirt` (WAIST, 1 armour): the mail skirt's cut in the scale shirt's scales; forged from iron | `scale_skirt`, `scale_skirt_over_leg_harness` |
| mh-fluted-legs | new `fluted_cuisses` (THIGHS, 2 armour): the cuisses with ridges, a roped lame at the top and the poleyn; forged from steel | `fluted_cuisses`, `_gilded` |
| mh-brigandine-cuisses | new `brigandine_cuisses` (THIGHS): cloth with rivet heads in rows over padded_cuisses' cut and a plate poleyn; forged from iron | `brigandine_cuisses` |
| mh-leather-cuisses | new `leather_cuisses` (THIGHS, 1 armour): boiled leather round the thigh and a cop over the knee; crafted from leather | `leather_cuisses` |
| mh-chausses | new `mail_chausses` (THIGHS, 2 armour): mail from the thigh to the toe (on the leg and the boot bones), laced up the back; forged from iron | `mail_chausses` |
| mh-schynbald | new `schynbald` (SHINS, 1 armour): a plate over the front of the shin only, buckled behind; forged from steel | `schynbald`, `_gilded` |
| mh-leg-wraps | new `leg_wraps` (SHINS, no armour, dyed): bands of cloth wound from ankle to knee; crafted | `leg_wraps` |
| mh-scale-sabatons | new `scale_sabatons` (boots slot): the sabatons in scales; forged from steel | `scale_sabatons`, `_gilded` |
| mh-mail-shoes | new `mail_shoes` (boots slot): the mail foot of the chausses, worn alone; forged from iron | `mail_shoes` |
| mh-hose | the new **HOSE** place (the last on `Harness.Slot`, so saved harnesses and guards' kits read with it bare), drawn as a sheet on the legs of the under-armour layer under everything: `shorts`, `breeches`, `baggy_breeches` (its cloth bagging out), `linen_hose`, `joined_hose` (the dye on the left leg and the dye it was made with on the right), `riding_hose` (leather let in at the knee and ankle). All dyed, crafted from string, wool and leather. It has its own slot on the harness screen, next to the shins. | `shorts`, `breeches`, `baggy_breeches`, `linen_hose`, `joined_hose`, `riding_hose` |
| mh-landsknecht-pants | new `landsknecht_hose` (HOSE): slashed with the second colour showing through, with puffs on the thighs, the left a size fuller ("one leg different"). Pairs with the body track's slashed doublet. Lore (new): "Hose and breeches" | `landsknecht_hose` |

## Guards

`Harness.UNDER_LAYERS` (what a guard who puts his armour by keeps on) now holds the padding, the mail **and the hose**, as the body task's note on the slashed doublet asked.

## Not done / not checked

- Not played; Java not compiled here (CI compiles). The hose layer (`UnderArmourLayer.Legs`) is new drawing code that has not been seen in the game.
- Guards are not issued hose or the new waist pieces by the guard kit; they wear them only if a player puts them on (or a spoil is salvaged).
- "Short Gambeson" has no piece of its own: the aketon and the padded skirt cover it.
- The slashed hose's "one leg different" is the puff on the left thigh being larger; the slits and colours are the same on both legs.
