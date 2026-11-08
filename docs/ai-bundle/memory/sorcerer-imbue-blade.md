---
name: sorcerer-imbue-blade
description: Kingdoms Sorcerer Imbue lives on the blade (custom data), survives drops/hand-overs, one imbued blade per sorcerer; PR #225
metadata:
  type: project
  modified: 2026-10-15T03:35:40.693Z
---
2026-10-15 Andy: "sorcerer imbuements carry over onto dropped weapons, because of this sorcerers can only imbue one weapon at a time".
Done in PR #225 MERGED 2026-10-15 03:38Z (pack 0.11.20 / Kingdoms 0.7.49; #224 also held 0.11.20 and must re-bump): `Spellcraft.Charge` in the stack's custom data (key jaymoskingdoms_imbue);
anyone (mobs too) wielding it gets the element; the sorcerer's newest stamp in `IMBUED` decides which blade is live.
Wind Cyclone reads the element from a blade held or carried. Related: [[kingdoms-sorcery]], [[wind-staff]].
