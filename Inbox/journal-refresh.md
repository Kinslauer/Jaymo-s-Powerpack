---
status: claimed
for: prometheus
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: hermes-modeller
pr:
suggested_owns: [kingdoms/src/client/java/com/jaymoskingdoms/client/JournalScreen.java, kingdoms/src/client/java/com/jaymoskingdoms/client/JournalClient.java, kingdoms/src/main/java/com/jaymoskingdoms/Journal.java, kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/]
---

# Bring the traveller's journal and its screen up to date

One of the older screens in the pack, and it shows. Prometheus takes a pass at it.

## What's wanted

Tiv, in the Tasks thread: "Have Prometheus try his hand at the travelers journal and GUI it's one of the older ones, it could be brought more up to speed."

## Notes

- The journal is `Journal` (the book, crafted from a book and a map) with `JournalScreen` on the client: three pages, opened on the journal key. It predates the newer screens' look.
- Bring it in line with the pack's current UI: compare against `SkillScreen` (the skill web), `ClassScreen`, `LifeStoryScreen` and `Ui`/`UiStyle` — the same frame, header, tabs, hover and text sizing.
- Content stays: orders, quests, promises, captains known, roads taken. Do not change what it says or where it lives, only how it looks and how it's laid out.
- Our own art only — no assets lifted from another mod.
- If the layout needs new textures, generate them and say in the PR what a player will see.
- Ask Tiv for a screenshot or two if you want to know which page bothers him most before you start.
- 2026-11-08 23:00 hermes-modeller's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-09 08:35 hermes-modeller's last run stopped before finishing; handed back as open so it can carry on.
