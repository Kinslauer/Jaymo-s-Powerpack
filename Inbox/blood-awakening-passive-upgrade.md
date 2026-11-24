---
status: in-review
for: claude-cloud
from: Andy (Claude chat, 2026-11-23)
asked: 2026-11-23
claimed_by: claude-cloud
pr: 2117
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWKNxU3D2FFpE1DbB4tCch4W
---

# Blood Awakening: everyone starts with it, the node upgrades it; Verdant gets push-button limb regrowth

## What's wanted

Andy (with a screenshot of the Blood Awakening node: Survival, Heritage, "Needs level 3", 1 point):
"everyone should start with their current blood awakening as a passive ability. this node should upgrade it. also
verdant should unlock their push button limb regrowth with this."

- **Start with it**: what Blood Awakening unlocks today (the skill-tree lock `TreeLocks.BLOOD`: each blood's own
  heritage-key move and held move, e.g. a Verdant's Stretch) is unlocked for every character from the start, without
  learning the node.
- **The node upgrades it**: Blood Awakening (`Skills.Perk.BLOOD_AWAKENING`, level 3, 1 point) becomes an upgrade to
  that move instead of the thing that unlocks it. Its description in `skill-locks.json` and the per-blood line drawn
  by `SkillScreen` (line ~1046) change to match.
- **Verdant**: learning the node also gives a Verdant a **push-button limb regrowth**: press a key to grow a lost limb
  back now, on top of the slow passive regrowth every 30 s (`ZFighterLineage.REGROW_EVERY`, `Dismember.regrow`).

## Where it lives

- The lock: `TreeLocks.BLOOD` (Kingdoms `TreeLocks.java:91`, Powers `core/TreeLocks`). It's checked in `HeritageMoves`
  (Powers, the held move), `EmberKindle`, `RaceHoldClient`, `HeritageKits:848`, `BioBuilt:260`, `RubberFolk:342`,
  `FrostPrison:106`. Grep `TreeLocks.BLOOD` and "Blood Awakening" across all three mods for every gate.
- The node: `Skills.java:415`, `SkillWeb.java:110`, `SkillIcons.java:111`, lang `skill-locks.json:74-75`.
- Verdant regrowth today: `ZFighterLineage` (`REGROW_EVERY`, `REGROW_SOUND`), `Dismember.regrow`, and Andy's ruling of
  2026-11-19 (PR #1889): regrown limbs come back unbroken, a Verdant's regrowth is a fleshy crunch with green blood
  spurting from the stump until the limb is back.

## Ask Andy before building

1. **What the upgrade is.** He didn't say. Offer a pick list (one or more per blood): a shorter cooldown, a stronger or
   longer version of the move, a cheaper cost, or a second step to the move. Ask whether each blood gets its own
   upgrade or one shared rule.
2. **The regrowth key.** Which key (the heritage key, the held move's key, or its own binding), its cooldown, and whether
   it costs anything (ki, hunger, health).
3. Whether players who already learned the node get their point back, since the node now does something new.
