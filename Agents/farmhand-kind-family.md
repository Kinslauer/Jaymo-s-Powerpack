---
status: running
task: farmhand-kind-family
branch: claude/farmhand-kind-family
with: 
pr: 
mods: kingdoms
versions: 
started: 2026-11-09
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/FarmhandHome.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Prejudice.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/talk.json
  - changes/kingdoms/farmhand-kind-family.md
---
# Family farmhand: a kind Heartlander family takes you in

**Task:** Make the Family farmhand start's family good-natured folk who take the player in regardless of race: no distaste, hearts, fear or trade cost, with race comments as flavour talk only.

## Plan
- Mark the family villagers as the farmhand household of the player (new `FarmhandHome.FARMHAND_FAMILY` attachment holding the player UUID)
- Exempt family villagers from minding the farmhand's blood in `Prejudice.minds` (the check `Peoples.meet` calls, also covers prices/trade, guard, hearts, prompt)
- Add `talk.jaymoskingdoms.farm_family.welcome` / `.notice` flavour lines, spoken once when the family takes the player in
- Add `changes/kingdoms/farmhand-kind-family.md` change note
- Build, pr_checks, open PR, set task in-review

## Notes
- Suggested owns listed Peoples.java, but the distaste check lives in `Prejudice.minds` (which `Peoples.meet` calls), so the exemption goes there and covers every distaste path (hearts, prices, guard, neighbour lean, prompt) at once.
- `Peoples.java` itself is unchanged: no version/MODLOG/gradle.properties edits (those are done at merge time by the release step).

## Done
What a player will see, and what wasn't tested.
