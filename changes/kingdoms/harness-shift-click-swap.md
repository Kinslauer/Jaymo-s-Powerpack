# Shift-click puts on every harness piece, swapping out what's worn
- **Shift-click swaps** (Andy: "you should be able to shift click all harness gear"): shift-clicking a harness piece
  whose place is already filled now puts it on anyway, and the piece that was worn there comes back into the slot the
  new one came from. In his clip the Heraldic Tabard just hopped between hotbar and inventory because the Cloak was
  in the Over place; now they change places. Before, shift-click only worked on a bare place.
- Works in the Harness screen (the thirteen harness places and the helmet, chestplate, leggings and boots slots
  beside them, so a helm or cuirass swaps too), in the survival inventory and in Jaymo's Powers' bauble screen
  (both through `HarnessQuickEquip.shiftClick`). The usual checks still hold: spaulders only over a chest piece, a
  crest or crown only on a helm, gilded plate only for a royal or lord, and in the Harness screen's armour slots a
  piece cursed with binding stays on.
- With a place filled, shift-clicking a spare piece of that place in the survival inventory now swaps it on instead
  of moving it between hotbar and inventory (drag it to move it).
- Shift-click on a worn piece in the Harness screen still takes it off into the inventory, as before.
- Covers every piece with a harness place (`HeraldryArmour.part`): padding (arming doublet, aketon, fur jerkin,
  chequered gambeson, linen shirt, wool tunic, cloth vest, leather jack, landsknecht doublet), mail (haubergeon,
  quilted coat, jazerant, hauberk, plated mail, scale shirt), shoulders (all spaulders, pauldrons, ailettes), arms,
  hands, thighs, shins, over-garments (tabard, surcoat, torn surcoat, giornea, cloak, torn cloak, monk's robe,
  kaftan, jester's motley, sash), hoods, hats and coifs, crests and crowns, neck, waist and hose pieces; in the
  Harness screen also the helms, cuirasses, coats and sabatons that go in the armour slots.
- Classes: `HarnessMenu` (`swap`), `HarnessQuickEquip.shiftClick`.
- Built in CI; not played.
