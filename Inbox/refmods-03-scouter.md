---
status: in-review
for: any
from: coordinator
asked: 2026-11-24
thread: Build Reference Mods pick list (cloud); queued by Hermes Training
claimed_by: 
pr: 2120
suggested_owns: []
---
# Reference Mods Patch 3: Scouter: hold to scan, full readings, tracking

Andy ticked these rows on the Reference Mods pick list (https://claude.ai/artifact/Fu1jDwywXNKSGj6mmU65fg, db picks/andy).
Full plan: Picklists/ReferenceMods-Plan.md, section "## Patch 3". Other mods' code and art are never copied
(ARR = remake ourselves). Any structure states where it generates and why (structure placement plan,
https://claude.ai/artifact/4foeFXysu1Vg9KJBADDZab). Change note per mod under changes/<mod>/<branch>.md.

## Patch 3: Scouter: hold to scan, full readings, tracking
Owns: powers zfighter ZFighterScouter + client.
Merges with Inbox scouter-bio-scanner-tracking.md. Andy (S8): the scouter IS the item that works for anyone, so the scouter reads for everyone and there is no separate handheld.

- **S1 Hold to scan** (Wan's Bio Scanner, size M). Wearing the scouter, hold its key to focus on what you're looking at: the lens zooms a little and the reading fills in line by line. Let go early and you keep only what came through. We have: The scouter reads name and power level at once.
- **S2 A full reading of a creature** (Wan's Bio Scanner, size M). All of that under the power level, in the scouter's green readout. Horses read our breed stats (speed, stamina, courage, temper); people read their realm and mood.
- **S3 It remembers kinds** (Wan's Bio Scanner, size S). Same, and a full scan counts as studying that beast for the Druid and for foe knowledge. We have: FoeStudy stars come from fighting.
- **S4 Plants and living blocks** (Wan's Bio Scanner, size S). Crops, saplings, bee nests, eggs, coral, moss: what stage, what it needs, how long.
- **S5 Track what you've scanned** (Wan's Bio Scanner, size M). After a full scan, tap the key to mark the target. A small chevron at the edge of the lens points to it and its outline shows through walls while it's within range, for a few minutes or until you lose it. One mark at a time on the basic scouter, more on better ones. Something hiding its ki can't be marked. We have: Inbox: scouter-bio-scanner-tracking is open.
- **S6 Forms it can take** (Wan's Bio Scanner, size S). For things that change: Z Fighters' forms you've seen, xenoform growth stages, a zombie's road to the horde. Unmet stages show as blanks.
- **S7 Reading the place** (Wan's Bio Scanner, size S). Look at open sky or ground and the scouter reads the place: heat or cold, and how dangerous the land is (the harsh-land world tiers), without naming anything lore-ish. A soft chime when a scan completes.
- **S8 A handheld scanner for anyone** (Wan's Bio Scanner, size S). A future-era handheld scanner for people without ki: the same readings without a power level. We have: Only Z Fighter gear reads creatures.
  - Andy's note: "the item that works for anyone, is the scouter"

## Folded in: Inbox/scouter-bio-scanner-tracking.md

# Scouter: bio scanner functions and tracking

**Andy's words:** "https://www.curseforge.com/minecraft/mc-mods/wans-bio-scanner this functionality should go on the
scouter and allow for tracking"

Read Wan's Bio Scanner's page and features (check its licence: if ARR, remake the behaviour ourselves, no copied code or
art). Put its scanning functions on our existing scouter and add tracking of a scanned target. Scanner UI only, no slash
commands, no discovery chat text. Change note under changes/powers/<branch>.md.

## Notes
- 2026-11-24 16:54 Pick-list rows S1-S8 for the bio scanner and tracking are on https://claude.ai/artifact/Fu1jDwywXNKSGj6mmU65fg; build from Andy's ticks there.
- 2026-11-24 17:21 project-thread-ojz7uj's claim had no sign of work for 30 min: handed back as open.
