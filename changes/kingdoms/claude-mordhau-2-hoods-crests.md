# Mordhau harness 2: hoods, hats, crests and neck
- Our own models of the historical pieces behind Andy's Mordhau picks (`kingdoms/tools/gen_heraldry_hoods.py`; nothing of Mordhau's is used), all worn in the harness:
  - **Hood place**: the tight hood, the monk's cowl, the cleric's mantle (hood and buttoned shoulder cape), the headsman's hood (black, over the face, eyeholes), the arming cap (quilted, 1 armour, worn under a helm), the linen coif; the bag hat, the archer's cap (feather), the field hat (straw), the hunter's hat (pinned brim, feather), the slashed bonnet (lining in a second colour, plumes), the fur hat, the turban and the veiled turban, the jester's hood (two colours, bells) and the victor's wreath. Cloth ones take a dye; the bonnet and the jester's hood take their second colour from the dye they're crafted with (`jaymoskingdoms:second_colour`). Hats are hidden under a helm.
  - **The mail coif's cuts**: lowered round the neck, square-cut, with a ventail, square-cut with a ventail (1 armour each; forged in iron at Jaymo's Powers' anvil).
  - **Crests**: the orle (a padded roll worn in the torse's place, dyed), the helm crown (a gold circlet round the helm, no torse), antlers, and the mantling (cloth from the helm's crown down its back, in the torse's dye).
  - **Any crown over a helm** (Andy: "you should be able to wear any crown"): every ruler's crown, tiara, circlet and coronet goes in the crest place too and is drawn round the helm. Anyone may wear one.
  - **Neck**: the armet's wrapper, a rounded plate over the armet's chin and cheeks, in every finish (forged in steel). A bevor or wrapper isn't drawn under the sallet, which has its own.
- Recipes for every cloth piece and crest; the new pieces count as cloth, mail or plate for Jaymo's Powers.
- `HeraldryArmour` (registrations, `SECOND_COLOUR`, `regaliaCrown`), `Harness.slotOf`, `HeraldryLayer`.
- Built and previewed off the game's files; not played.
