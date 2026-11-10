---
status: blocked
for: quill
from: claude (Hermes thread, Andy's request)
asked: 2026-11-09
thread: Claude Hermes-training thread
claimed_by: 
pr: 
suggested_owns:
  - E:/Jaymo Vault Live/Lore.md
  - E:/Jaymo Vault Live/Lore-before-rewrite.md (new, a copy of the page as it was)
---

# Lore: rewrite the canon and give every element its place

**Andy's words (2026-11-09):** "have him rewrite the current canon and add the rest of our elements that arent yet
spoken for"

**This is a vault page, not code:** no worktree, no branch, no build, no PR. You edit `E:/Jaymo Vault Live/Lore.md`
directly (the vault sync keeps its history).

**What's wanted:**
1. First copy the page as it is now to `E:/Jaymo Vault Live/Lore-before-rewrite.md`, so Andy can compare.
2. **Rewrite the current canon** (every section from "The ages of the world" to "The sea"): clearer and better to read,
   in one voice, but keep every fact. Nothing in it changes meaning, nothing is dropped, nothing contradicts it. Keep
   "Rules for writing against this" and "Where this canon lives in the game" as they are, apart from wording.
3. **Add the elements not yet spoken for.** Go through the three mods and list every player-facing element the page
   doesn't cover yet: powers and power-holders, classes and costumes, mobs and bosses, structures and dungeons,
   items and materials, Kitchen's foods and crafts, Kingdoms' systems. Find them with `E:/Origins Mod/docs/codemap/`
   (`powers.md`, `kingdoms.md`, `kitchen.md`) and the mods' `lang/parts/*.json` and `en_us.json` names. For each one,
   write where it sits in this world, consistent with the canon and with what the element actually does in the game
   (its lang text is the truth about what it does). Put each in the section it belongs to, or a new section.
4. Mark every new addition so Andy can find it: start the paragraph with `(new)`. He'll approve or change them.
5. At the end of the page, keep a short `## Not yet placed` list: elements you found but couldn't place without
   inventing something big, each with one line on why.

**Fable only (Andy, 2026-11-09):** do this task only on Claude Fable 5.2 (`claude-fable-5.2:free`). Check the
model you're running on before you start and before each section. If it's anything else (the free allowance ran out
and you were moved to a fallback model), stop right there: save what you have, then run
`... vault.py --note lore-canon-rewrite "Fable allowance ran out at <section>; done so far: <list>. Ready for the API agent to take over."`
and `... vault.py --set lore-canon-rewrite status=blocked`, and end the run. Another agent on Andy's API key finishes it.

**When done:** run `C:/Users/toopo/AppData/Local/Programs/Python/Python312/python.exe E:/hermes/powerpack/vault.py
--note lore-canon-rewrite "Lore rewrite: <how many sections rewritten, how many (new) entries, how many not placed>"`
then `... vault.py --set lore-canon-rewrite status=blocked` (blocked = waiting on Andy to read it; there is no PR to
review). If the page is too big for one run, finish what you can, note where you stopped, and leave the task
`claimed` for the next run.

## Notes
- Read `lore-read-the-canon` first: the same rules apply (if the mod contradicts the page, say so in a note here
  rather than changing the canon).
- Andy's standing rule: patch notes for testers stay short and leave out most lore.
- 2026-11-09 21:40 Held until Quill's free Claude Fable 5.2 allowance resets (10:31 PM, Andy: 'let quill do it on fable when that resets'); reopened automatically at 10:33 PM.
