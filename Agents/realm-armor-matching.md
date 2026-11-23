---
status: running
task: realm-armor-matching
branch: claude/gifted-kalam-84de98
with: Claude Code (desktop session, started from local_2bfd1f34)
pr: 
mods: kingdoms
versions: 
started: 2026-11-22
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/GuardKit.java
  - kingdoms/src/main/java/com/jaymoskingdoms/LifeStory.java (the realm figure only)
  - kingdoms/src/main/java/com/jaymoskingdoms/Outfits.java (Heraldry)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/LifeStoryScreen.java (realmFolk only)
---
# Realm armour matching

**Task:** Andy saw five realms on the life story's realm page all in the same armour (only Morstead differed). No two
realms should wear identical armour; the page and the realm's people in the world should match.

## Plan
- Trace why the realm figure comes out the same (GuardKit's free floor and mail replace Outfits' per-realm dress; the
  page doesn't carry the harness, so the tabard never shows).
- Give each realm a mark of its own that survives GuardKit, carry the harness to the page, stop the page's figure
  spending the realm's stores, add a check.

## Notes
- No builds on the PC (memory): CI builds the PR.

## Done
