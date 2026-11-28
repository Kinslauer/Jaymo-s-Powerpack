# A shield on F is held up in front again; shield bash and bang are back, and bash works bare-handed
- **Andy: "the blocking animation needs to be fixed ... the shield is not held infront of the player, also the shield
  bash and taunt are gone."** In Combo Clash's combat mode (on for everyone) F went to Combo Clash's own block, which
  with a shield plays its `shield_block` motion: in first person its arms model points the arm end-on at the camera with
  the shield turned aside. Our raised shield never came up, so the shield bang (the clang that
  draws hostiles to you, the taunt) and the held-click shield bash had nothing to work on, nor had the shield moves
  (throw, slam, rise, charge, brace, ride down).
- A shield in hand is now always ours on F, in every mode (`GuardKey.shieldGuard`): `GuardKeyClient` raises it even in
  combat mode, and Combo Clash's block stands aside for it (`ComboClashClient.blockYields`, read by `ClientCombatMixin`).
  It's vanilla's shield raise again: held up in front in first and third person, with our guard, parry and perfect parry.
  Fists and weapons without a shield still block with Combo Clash's block, as before.
- **Shield bash without a weapon** (Andy: "the shield bash should work without a weapon by holding m1 while f is held"):
  with the shield up on F and nothing in the main hand, holding the attack key charges the bash and letting go throws it,
  the same bash as with a weapon (`ShieldBang.ready`). A short click bare-handed is still the shield throw (now sent on
  release, so a hold can become the bash). The bang (taunt) still needs a weapon to bang the shield with
  (`ShieldBang.bangs`).
- Built; not played.
