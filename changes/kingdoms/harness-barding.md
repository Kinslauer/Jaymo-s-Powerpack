# Harness 4: matching barding and caparisons

## What the player will see
- Chain and plate barding now come in three finishes (bright, blackened, gilded) matching the rider's heraldry armour finishes.
- New caparison item: a cloth cover worn over barding, dyeable in realm colours with banner patterns.
- Caparison slot added to the tack screen under the banner slot.
- Barding finish displayed in inventory via select model (bright/blackened/gilded icons).
- Caparison rendered over barding on horses, tinted with the caparison's dye colour.

## Changes
- **Tackroom.java**: Added `CAPARISON` item, finish support for barding (reusing `HeraldryArmour.FINISH` component), `finish()`, `gilded()`, and `finished()` helper methods.
- **Tack.java**: Added `caparison` field (slot 3), updated `CODEC`, `STREAM_CODEC`, `NONE`, constructors, `get()`, `with()`, `empty()`, and added `caparison()` predicate.
- **TackMenu.java**: Added caparison slot (slot 3) at `CAPARISON_Y = 108`, updated `OWN = 6`, `INVENTORY_Y = 140`.
- **BreedHorseRenderer.java**: Added `armorFinish` and `caparisonColor`/`caparisonPatterns` to `State`. Updated `extractRenderState()` to read barding finish from `Tackroom.finish()` and caparison from banner data. Updated `armorFor()` to select texture by finish (`textures/entity/breed_horse/armor/<asset>_<finish>/<build>.png`). Added `ArmorLayer` caparison rendering over barding.
- **TackLayer.java**: Added `caparison` field to `Look` record. Updated `extract()` to read caparison dye colour from `DataComponents.DYED_COLOR`.

## Not tested in game
- Visual verification of barding finishes (bright/blackened/gilded) on all horse breeds.
- Caparison rendering over barding with banner patterns.
- Tack screen UI with new caparison slot.
- Finish component persistence and sync on barding items.