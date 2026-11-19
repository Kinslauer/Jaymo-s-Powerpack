# crits - riposte kinds, crit cue, camera punch, heavy crit ring, combat attributes
Andy's Hackers 'n Slashers picks (All Rights Reserved: ideas only; code, sounds and look are ours).
- **Riposte by weapon** (`Guard#riposte`): a great weapon staggers and slows, a one-handed blade opens a bleed, a knife or dagger lands a quick second hit, a polearm shoves the foe back.
- **Crit cue** (`combat/CritCue`): every riposte, perfect parry and jumping crit rings a short synthesised sound (`blade/crit_1..3`, `tools/gen_blade_sounds.py`) and a small white flash. `CritCue.cue(level, at)` is public for Kingdoms.
- **Camera punch**: landing a cue shakes your own screen; `/critpunch off` turns it off.
- **Heavy crit**: a jumping crit with a great weapon, warhammer or mace throws a ring (MagicFx) and knocks back those near the foe.
- **Attributes** `riposte_power`, `sneak_attack_power`, `headshot_power`, `parry_recharge` (multipliers, base 1). Guard reads riposte power and parry recharge (a shorter lockout after a missed raise). Raised by existing gear only: rapier, daggers/knives, recurve bow held; Brawler's Belt (riposte) and Swiftfoot Ring (parry recharge) worn.
- Compiles in CI; not played.
