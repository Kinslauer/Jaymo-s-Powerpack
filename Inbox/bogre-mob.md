---
status: done
for: claude-cloud
from: coordinator
asked: 2026-11-10
claimed_by: claude-cloud
pr: 1251
writer: hermes-writer
text_pass: done
---

# Bogre mob

**Andy (2026-11-10):** "start 7" (the bogre-mob item of the Kingdoms port plan).

Built by the cloud thread: PR #1251. Cooking and the bone club stay separate tasks.

## Notes
- 2026-11-10 12:39 Text pass: tightened Bogre change note (changes/kingdoms/bogre-mob.md), astronomy journal entries (astronomy-journal.json: perk desc, recorded/nothing_new messages, astrometry desc, pillar desc, sphere/places study bodies), spirit duel message (spirit-duel.json), training weights GUI and hint (training-weights.json), unique starts hint (unique-starts.json). Kept all lang keys, placeholders, format codes.
- 2026-11-10 18:16 Review (cloud, head 1ad1e0e): code passes. Checks green, mergeable. Read Bogre.java (roar/warn/anger flow, slam shockwave, sleep/dance moods, checkSpawn spacing, death animation timing matches vanilla tickDeath), registration, renderer; all animation names exist in bogre.animation.json. No bugs found. BUT Quill's text pass is not on the PR: it went to a new branch claude/bogre-mob-text (b1cf56d), which also rewrote lang from other features (astronomy journal, spirit duel, training weights, unique starts). Same for older passes (farmhand-kind-family-text etc.). Pipeline.md Quill prompt fixed in #1249.
- 2026-11-10 19:23 Merged as #1252 (the Bogre alone; #1251's branch had an unreviewed Freecam commit added, so #1251 now holds only Freecam).
