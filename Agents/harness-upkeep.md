---
status: stalled
task: harness-upkeep
branch: claude/harness-upkeep
with: 
pr: 
mods: kingdoms
versions: 
started: 2026-11-18
owns:
  - 
---

# Harness 5: upkeep (dents, rust and oil, fitting)

**Task:** Implement three upkeep features for Heraldry harnesses: (1) Dents and scratches - plate shows wear from its durability, (2) Rust and oil - plate left wet rusts and weakens; oiling keeps it bright, (3) Fitted harness - an armourer fits a harness to one player: a little more armour, slows less.

## Plan
- Examine existing HeraldryArmour, HeraldryWear, Harness, HarnessMenu/HarnessScreen, HeraldryLayer, and gen_heraldry_armour.py
- Implement wr-dents: visual wear based on durability
- Implement wr-rust: rust mechanic when wet, oil to prevent/remove rust
- Implement wr-fitting: armourer fitting for bonus armour and reduced slowdown
- Add change notes in changes/kingdoms/
- Build and test

## Notes
- 2026-11-18 02:45 Andy approved the lore placement "The armourers' wider makes" (2026-11-18): it's canon now.
- 2026-11-18 19:34 hermes-a's last run stopped before finishing; handed back as open so it can carry on.
- Source: Harness Pattern Book artifact, rows wr-dents, wr-rust, wr-fitting

## Done
- 2026-11-19 07:59 No sign of work for 30 min: marked stalled (whoever ran it stopped).
