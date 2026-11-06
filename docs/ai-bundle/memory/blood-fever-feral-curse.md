---
name: blood-fever-feral-curse
description: Vampire blood fever + werewolf feral curse + Great Ape rage takeovers (symbiote-style holds), merged 2026-10-24 (#483, via combined merge #490)
metadata:
  type: project
  modified: 2026-10-25T03:44:41.859Z
---
2026-10-24 Andy: vampire and werewolf get their own takeover system like the symbiote's, "slowly losing yourself to the blood or the curse"; werewolf grows more feral, vampire struggles against blood fever or gives in.
Built in Kingdoms: `Seizures` (shared hold; client SeizureClient walks you at prey, SeizeInputMixin swallows keys, each press a heave), `BloodFever` (FEVER + SURRENDER attachments; urge seizes you to drink; fight or give in with U = WerewolfKey), `FeralCurse` (FERAL 0-100, 5 stages: whim hunts as beast, wild changes off the full moon that you can fight down, refusals to change back). Design summary in docs/Vampires-Plan.md "Losing yourself".
PR #483 merged 2026-10-24 22:24Z (with combined #490), not playtested yet. Related: [[vampires-werewolves]], [[symbiote-roar-tethers]], [[vampire-blood-vision]].
2026-10-24 20:0xZ Andy: "what does an ape takeover look like to you, add that and lemme see". Added to #483 in Powers: `ApeRage` (rage builds as a Z Fighter ape, full = fit using the wild ape's berserk walk, struggle keys in ZFighterApeInputMixin; PRIMAL kept 0-100). Notes in docs/ZFighter-Plan.md. [[zfighter]], [[wild-great-ape]].
2026-10-24 Andy: winning ape fits several times unlocks hidden Super 4 (red long hair, red primal fur, fire and sparks). Built in #483: 5 won fits (`ApeRage.TAMED`), Saiyan only, C from highest form, `ZFighterPrimalFur` layer, `/powerdebug zfighter ape super4`.
2026-10-25 Andy: Super 4 renamed Primal Super; full particle storm only for the power-up blast (POWER_UP_TICKS), then settled aura + rising debris; fur GAP 0.36 to sit over MCA clothes; 6 Super hair sets (swept/wild/flame/gale/crown/fringe) by UUID; debug `/powerdebug zfighter ape primalsuper`. PR #498 merged 2026-10-25 as pack 0.16.5.
2026-10-25 Andy: ape left click = chargeable punch (ApeSmash, no swing/dig); Shoulder Surfing hid the giant ape (its own camera inside the ape's width) -> ShoulderSurfingGiantMixin; powers screen Mode slot named per power, Z Fighter's = Fusion (PowerClient.screenMode); Primal fur worn via CostumeModels.worn. PR #499 MERGED 2026-10-25 02:57Z as pack 0.16.6.
2026-10-25 Andy: ki glow like Create: Lanterns' halo, "faint, but apparent" -> ZFighterKiGlow (3 additive energySwirl shells, ki/form colour). PR #501 MERGED 2026-10-25 03:44Z (pack 0.16.7).
