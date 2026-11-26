---
name: pick-page
description: Make a pick list or checklist Andy can tick, as a page he opens in his browser, and write his picks back into the vault. Use whenever Andy should choose from a list (ideas from a mod, rows to build, questions before building), or asks for a pick list, checklist, "tickable" list or "what needs me".
---
# Pick pages for Andy

Andy can't tick a markdown pick list in Codex: it shows the raw file (frontmatter, `- [ ]`, `^ids`). So every list he
picks from goes to him as a page: one HTML file, made by `tools/pick_page.py`, that opens in any browser with no
internet. He ticks rows, writes notes, answers questions, then presses **Copy picks** (and pastes to you) or
**Save picks** (a `.picks.txt` in his Downloads). You write that back into the list with `--apply`.

Never show Andy the markdown itself or ask him to edit it, and never build anything from a list before he has picked.

## 1. Write the list (markdown, in the live vault)

File: `E:/Jaymo Vault Live/Picklists/<Name> pick-list.md`. Copy the shape of an existing one
(`Picklists/Small-armies pick-list.md` is short and clean). The script reads exactly this format:

```
---
type: picklist
source: <what Andy linked or asked, in a few words>
url: <links>
licence: <each source's licence> · verdict: <port | ideas only | install>
status: waiting-for-picks
created: <YYYY-MM-DD>
tags: [inbox, picklist, <topic>]
---

# <Name> pick-list

One short paragraph: what this is and the verdict up front (does any of it run on our 26.3 Fabric pack?).

## Questions                          (only if something must be decided before building)

### Whose voice can a changeling copy? ^who
One line of context.
- (*) **Any other player on the server** ^anyone
  What happens if he picks it.
- ( ) **Only players you're close to** ^bonded
  What happens if he picks it.

## <Part, e.g. the mod's name>

### <Group, optional, e.g. a blood or a feature area>
- [ ] **What the player gets, in plain words** `M` ^cs-soldiers
  One or two lines on what it is.
  For us: how we'd build it on our own classes.
  Notes:
```

Rules for the content:
- Row ids: unique in the file, lowercase, a short prefix per source (`cs-`, `hog-`, `ba-`). Questions and options get
  ids too (`^who`, `^anyone`). The script refuses duplicate ids.
- Size `S`, `M` or `L` on every row. One idea per row, so a tick means one thing.
- Every question marks one option `(*)`: your suggestion, picked by default. Give 2-4 options.
- Check before you write: licences (CLAUDE.md: ideas only unless the licence allows a port), what we already have
  (`docs/codemap/`), Andy's rulings (`docs/AI-HANDBOOK.md`, `Live/Decisions.md`). Never invent a class, mod or ruling.
- Cover the whole ask, row by row; if you leave something out, say so in the intro (no silent cuts).
- A heading with no rows under it (`## Already ours`, `## Notes`) shows as plain text on the page.

## 2. Make the page

```
python tools/pick_page.py "E:/Jaymo Vault Live/Picklists/<Name> pick-list.md"
```

It writes `<Name> pick-list.html` beside the .md and prints the row and question count. Check the count matches what
you wrote. If it says no rows were found, a row line doesn't match `- [ ] **Title** `S` ^id` exactly.

Then sync the vault (`python tools/vault_sync.py`).

## 3. Hand it to Andy

Give him the page as a clickable link and one line on what to do, for example:

> Pick list ready: [Small armies](file:///E:/Jaymo%20Vault%20Live/Picklists/Small-armies%20pick-list.html).
> Tick what you want, then press Copy picks and paste it here.

Use the `.html` link, never the `.md`. Spaces in the path are `%20`. If his message is about several lists, give one
link per list.

## 4. Write his picks back

When he pastes text that starts with `PICKS v1`, or says he saved it:

```
python tools/pick_page.py --apply "<the .md>" "~/Downloads/<Name> pick-list.picks.txt"
python tools/pick_page.py --apply "<the .md>" -          # or pipe the pasted text in on stdin
```

This ticks his rows (`- [x]`), puts his notes on the `Notes:` lines, writes `**Andy's answer:** ...` under each
question, and sets `status: picked`. Then:
- Append one line to `E:/Jaymo Vault Live/Decisions.md`: the date, the list, how many rows he ticked, his answers and notes.
- If an Inbox task waits on these picks, add a note to it (`python tools/vault_sync.py --note <task> "..."`) and set it
  back to `open` so its bot can build.
- Re-run step 2 so the page shows his picks if he opens it again. Sync the vault.
- Plan the build only from the ticked rows; name any ticked row you can't build and why.

Claude in the cloud makes the same lists as claude.ai pages instead; both read and write the same markdown.
