# symbiote suit takes a worn costume's shape
- **The costume is the suit** (Andy: "any symbiote user with a costume has the symbiote take that shape for its suit
  instead, so it can be called on and off at will like the default suit does"). Anyone bonded to a symbiote (any strain,
  web slinger or not) who puts a costume on no longer wears cloth: the symbiote takes the design. The X menu's Suit tile
  calls it and withdraws it. Withdrawn, you look like yourself and your armour shows; called, you wear the costume in
  its own colours, with its mask, cape, emblem and suit-editor pieces.
- **Forming**: called, the tendrils wrap and the goo covers you as before, then the costume folds over the goo part by
  part where the plain suit used to. Withdrawn calmly, it goes back the same way. Torn off (the menu toggle, a
  rejection, a fight for it) it turns back into the strain's plain suit and tears away as it always did.
- **Bigger forms**: Living Armor keeps the costume with its tendrils and weapon arm; the big suit swallows it and it
  comes back when the big suit goes.
- **Mask and identity**: the mask key and the X menu's Mask chip both lift the costume's head piece. A face-hiding mask
  or hood only hides who you are while the suit is called (`SecretIdentity` follows the Suit flag).
- **First person**: your sleeve is the costume's while the suit is called, bare while it is withdrawn.
- Unchanged: `/costume off` and the Costume Table still hand the costume back; dying still leaves it on your body;
  a host whose symbiote leaves wears it as plain cloth again.
- Classes: `costume/Costume` (`symbioteShape`, `shown`), `client/costume/CostumeClient` (`SHAPE`, `forming`, `laid`),
  `CostumeModels.Worn`, `WebSymbioteLayer`, `WebSymbioteHand`, `WebSuitGrowth.suitLaid`, `WebSymbiote.set/toggleMask/unmasked`,
  `BodyCover.ownArms`.
- Compiled; not played. Expect tuning on how the costume folds over the goo, and the jump from costume to plain suit
  when it is torn off.
