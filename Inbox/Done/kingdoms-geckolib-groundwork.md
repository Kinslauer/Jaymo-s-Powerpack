---
status: done
for: claude
from: coordinator
asked: 2026-11-08
thread: Claude reviewer session (Andy: Inhabitants and Stormborn Tomb become Kingdoms additions)
claimed_by: claude
pr: 1149
suggested_owns:
  - kingdoms/build.gradle (GeckoLib dependency)
  - kingdoms/src/main/java/com/jaymoskingdoms/inhabitants/ (new package)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/inhabitants/ (new package)
  - credits / license notices
---
# Kingdoms: GeckoLib groundwork for the Inhabitants and Stormborn ports (Claude session)

**Andy's words (2026-11-08):** Inhabitants "is going to be a kingdoms addition"; Stormborn Tomb: "we need it".

**What's wanted:** the base every later mob task builds on, done once by a Claude session so the Hermes bots can copy
the pattern:
- GeckoLib as a Kingdoms dependency. It already ships in the pack (root `build.gradle`, "GeckoLib (MIT, Tslat and
  contributors)", `maven.modrinth:geckolib:kSxHvs99`); use the same artifact so the versions match.
- A new `com.jaymoskingdoms.inhabitants` package with one tiny example GeckoLib mob wired end to end (entity type,
  attributes, spawn egg, GeckoLib model/renderer, spawn placement), documented as the pattern to copy.
- Credits: Inhabitants by JeremySeq / Team Synapse (MIT, "just don't forget to credit us properly"), Stormborn Tomb
  by Tada_no_ahiru (MIT), DungeonZ (MIT), FrogiLib (MIT): license texts kept with the code that uses their work, and
  named in the MODLOG. Draugr Invasion (All Rights Reserved) and Supplementaries (own license) are NOT copied at all.
- A short section in `docs/AI-HANDBOOK.md` or a reference note: "how to add a GeckoLib mob to Kingdoms", which the
  bots will follow for the Bogre, Impaler, Warped Clam and tomb mobs.

**Where to start:** Inhabitants source `https://github.com/JeremySeq/Inhabitants` (MIT; it timed out on 2026-11-08,
else take the 1.2.1 jar, CurseForge project 1436034) for how they register GeckoLib mobs on Forge 1.20.1, then the
GeckoLib API of the version the pack ships, against the 26.3 sources.

## Notes
- Full plan and order: `Kingdoms port plan.md` in the vault's Live folder.
- 2026-11-08 15:34 PR #1149 is merged into master; moved to Inbox/Done.
