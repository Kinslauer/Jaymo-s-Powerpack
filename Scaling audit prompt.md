# Scaling audit: prompt for the coordinator

Written 2026-11-17 for the coordinator to run when its usage resets. Paste everything below the line.

---

Andy, after playing today: "I like how the early game feels apart from bugs. You are pretty pathetic, you have to grind for everything you get. But the game NEEDS to scale correctly."

**Goal.** Audit how power scales across every progression line in Jaymo's Powers, Kingdoms and Kitchen, from a fresh start to the late game. Find where the curve breaks: too fast, too flat, a dead end, or a cliff. Keep the early game exactly as it is: weak, earned, a grind. This is a read-only audit. Change no code. The result is a pick-list for Andy.

**Read first.** `docs/HANDOFF.md`, `docs/vault/Home.md`, the codemaps (`docs/codemap/powers.md`, `kingdoms.md`, `kitchen.md`), and Lore.md in the live vault. Tuning lives in the `static final` constants at the top of each class: quote the real numbers and the `file:line` for every claim. Check against `origin/master`, not memory.

**The lines to cover.** For each line, say how you get it, what it gives at the start, middle and end, how long it takes (grind time or the gate), and what it stacks with. Find each line in the codemaps; don't guess.

1. **Powers:** each power's own growth (mastery, levels, forms, ultimates), Z Fighter forms and zenkai, the symbiote bond, the Rig and its augments, corruption and pacts.
2. **Character:** the Kingdoms skill tree (every pillar and perk, ranks, tree locks), classes and second callings, heritages and life-story starts, flaws, feats and paths, attributes and stats.
3. **Faith and curses:** patron tiers and boons, the wrath ladder, vampire and werewolf gifts, Nightborn blood.
4. **Gear:** weapons and armour tiers (vanilla, tinkering parts, patron weapons, the future gun gear, Frontier and Knights picks), enchanting, plate and gilding, consumables, potions, food buffs from Kitchen.
5. **Money and things:** silver and coin income per hour at each stage, prices, trade, realms and what owning land gives.
6. **The other side of the curve:** mob health and damage by area, dimension and difficulty; Hard is the server's setting now. Also bosses and patron champions (their tier scaling), group scaling, the Retaking acts, and stealth and sneak attacks (now perk-gated).

**What to look for.**
- **Stacking:** multipliers that compound across lines, such as damage × sneak attack × patron boon × class × gear × food. Work the product out for one realistic late-game build per archetype (melee, caster, stealth, gun, Z Fighter) and compare it with what the hardest content can take.
- **Shape:** spikes, where one unlock jumps far past the step before it. Plateaus, with nothing worth earning for a long stretch. Dead ends and caps, where a line stops mattering. Cliffs, where a threat outscales everything a player could have by then.
- **Early leaks:** anything that breaks the early grind, i.e. strong power with no cost or gate (like the free ×2 sneak attack we just gated).
- **Content mismatch:** the stage a player can reach a zone or boss at, against the stage the zone or boss is tuned for.
- **Missing endgame:** what a late-game player still has to chase.

**How to run it.** Follow CLAUDE.md:
- Scanning is read-only and goes to `scanner` agents (Haiku 5.5), one line or group of lines each, at most 2 at a time because of usage limits.
- Every finding gets an independent verifier agent before it is reported.
- Each scanner returns the line's curve as numbers (start, middle, end), its gates, what it stacks with, and its `file:line` sources.

**Output.**
1. **A short report:** the curve of every line on one table, the stacking worked out per archetype, and the ten worst problems ranked.
2. **A pick-list artifact for Andy:** the tickable page with notes, in the coordinator's row format, one row per proposed fix. Each row gives the problem with its numbers, the change in tuning terms (which constant, from what to what) or the small system it needs, its lore fit, and how much work it is.
   - Group the rows by mod and line, so Andy's picks can be split into parallel tracks, one per line, like `docs/Plan-0.8.md`.
   - Anything that would make the early game easier is out, unless it fixes a bug.
3. **Vault notes:** post open questions under the task's Notes, and record Andy's picks in Decisions.md.
