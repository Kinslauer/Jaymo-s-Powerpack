---
status: stalled
task: medieval-patch10-temple-patrons
branch: claude/medieval-patch10-temple-patrons
with: 
pr: 
mods: kingdoms
versions: 
started: 2026-11-19
owns:
  - kingdoms temples / patron pairing
---
# Patch 10: which patron each old-age temple calls

**Task:** Pair each old-age temple site with Andy's assigned patron (Hearthkeeper→Hearth-Warden etc.) instead of drawing the patron at random, in cv-avatars / cv-ruins (Kingdoms).

## Plan
- Find temple patron-selection code in Kingdoms (cv-ruins temples)
- Hard-code each old-age site's patron per Andy's table
- Build Kingdoms, run pr_checks, commit, push, open PR
- Set task status in-review

## Notes
- Patch 10 already merged; Temples still draws each site's patron at random.
- Andy's pairings: Hearthkeeper→columned plains temples, Anvil-Father→mountain/mine halls, Deep Mother→sunken sea-floor colonnades, Green Huntsman→wooded grove shrine, Lantern Warden→stepped sand tombs, Crowned Sun→north long halls/burial mounds (not moor barrows), Ash Whisperer→Blossom walled palace, The Truth→Aether house.

## Done
What a player will see, and what wasn't tested.
- 2026-11-19 21:06 No sign of work for 30 min: marked stalled (whoever ran it stopped).
