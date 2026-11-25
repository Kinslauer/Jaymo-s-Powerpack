---
status: running
task: mordhau-1-helms
branch: claude/mordhau-1-helms
with: claude (PC worker, Opus 5.5)
pr: 
mods: kingdoms, powers
versions: 
started: 2026-11-24
owns:
  - kingdoms/tools/gen_heraldry_armour.py (new helm builds and the helm lists only)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/bbmodels/heraldry_<new helm>.json and their textures, icons, item files
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryArmour.java (helm registrations)
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryWear.java, GuardKit.java (the realm kettle cut only)
  - src/main/java/com/jaymospowers/tinkering/smithing/HeraldryForging.java (new helms forgeable, painted barbutes)
  - changes/kingdoms/claude-mordhau-1-helms.md, changes/powers/claude-mordhau-1-helms.md
---
# Mordhau harness 1: helms

**Task:** Andy's 13 picked helm rows from the Mordhau pick list (Inbox `mordhau-1-helms`): our own models of the
historical helms each row is based on, made the way the harness makes its pieces; the kettle hat in a different cut
for different realms.

## Plan
- New helms in gen_heraldry_armour.py, registered in HeraldryArmour, forgeable at Powers' anvil.
- "have" rows: barbute and spangenhelm made forgeable; painted barbutes at the anvil.
- Kettle cuts by the realm's harness style (GuardKit/HeraldryWear).
- Previews to `Assets/mordhau-1-helms/`; lore (new) paragraphs for gallowglass, turban and spectacle helms.

## Rows
| Row | Built |
|---|---|
| mh-kettle (have) | kettle_hat kept (already forgeable); two new cuts, `broad_kettle` (low bowl, broad brim turned down: the Spanish cut) and `tall_kettle` (tall bowl, narrow sloping brim: the eisenhut). Realm cuts: plate realms tall, tabard realms round, half-plate realms broad (`HeraldryWear.KETTLE_CUTS`, by `Outfits.Heraldry.harness`). Orle = the torse we have; strap on every kettle. |
| mh-kettle-slitted | `deep_kettle`: deep bowl, brim round sides and back, the front brim pulled down with two sights; the front is a visor bone, so up = "Raised". |
| mh-kettle-sallet | `kettle_sallet`: sallet skull and short tail with a kettle brim, no visor, no bevor. Bevored = wear the bevor (NECK), previewed. |
| mh-sallet-open | `archers_sallet` (open, brow cut high, short tail) and `long_sallet` (very long tail, front closed to one sight; the front lifts: "Raised"). Plumes = crest_plume. |
| mh-gallowglass | `gallowglass_helm`: tall conical open bascinet, mail aventail. Lore (new) paragraph. |
| mh-barbute (have) | barbute re-cut: its T-opening now actually shows the face. Made forgeable; painted finish offered at the anvil. Plume = crest_plume. Crowned/Champion's barbute waits on mh-crown (track 2). |
| mh-barbute-visor | `visored_barbute`: the re-cut barbute plus a visor bone (sight, ridge); painted at the anvil too. |
| mh-greathelm-visored | `visored_great_helm`: flat-topped great helm, the face a visor bone. "Visored Flat Top" is the same helm. |
| mh-flattop-open | `flat_top_helm`: flat-topped round-sided helm, open face with a nasal. Royal = crown (track 2), Covered = covered finish (track 3): not built here. Orle = torse. |
| mh-conical-nasal | spangenhelm (have) made forgeable; `nasal_helm` (smooth one-piece cone, band, nasal, no mail: the Norman casque) and `spired_helm` (tall cone to a spire, nasal, mail aventail: the Rus cone). "No Nasal Guard" sub-variant not built (the skullcap is the bare cap). |
| mh-spectacle | `spectacle_helm`: banded round bowl, spectacle mask, mail curtain. Fixed face. Lore (new) paragraph. |
| mh-skullcap | `skullcap`: plain cap sized to sit over the mail coif (previewed both ways). |
| mh-turban-helm | `turban_helm`: bulbous fluted bowl, spike, nasal, mail aventail (a new model, not a re-cut of veiled_helm). Lore (new) paragraph. |

## Notes
- The new helms are not in `HeraldryArmour.HELMS` (a realm's own helm is picked from it by position; adding would change every realm's).
- Guards are issued only the kettle cuts; whether they should wear the others is a question for Andy (Inbox note).

## Done
What a player will see, and what wasn't tested.
