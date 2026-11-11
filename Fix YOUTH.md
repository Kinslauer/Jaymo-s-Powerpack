# Fix YOUTH

From the YOUTH audit (`YOUTH report.md`, Nov 10). Andy said "do them both, fix youth's bad behaviors on the whole".
Written by the cloud Claude (Odysseus). **For Claude Code on Andy's PC**, which can reach `E:/hermes` and build.
Work top to bottom, tick each box, and write one line under it saying what you changed. Never print a token or key.

## 1. Waterskins: old saves keep their waterskin (repo PR)

PR #1176 replaced `jaymoskitchen:waterskin` with `waterskin_leather`, `waterskin_iron` and `waterskin_dragon`, and
mapped nothing. So any waterskin in an inventory, chest or on the server from before 0.5.98 is deleted on load.

- [x] In `kitchen/src/main/java/com/jaymoskitchen/climate/Climate.java`, right after `WATERSKIN_LEATHER` is
  registered, make the old id resolve to the leather waterskin. Vanilla does this for renamed blocks with a registry
  alias (`addAlias(oldId, newId)` on the item registry). **Check the 26.3 sources first** (`./gradlew genSources` in
  `kitchen/`): the method's name, whether `BuiltInRegistries.ITEM` exposes it, and whether item stacks read from a
  save go through the alias. If there's no alias, do the same with a small mixin on the registry lookup.
  - 26.3 has no registry alias API, so this is done with a mixin. New `MappedRegistryMixin` rewrites
    `jaymoskitchen:waterskin` to `waterskin_leather` in `MappedRegistry.get/getValue(Identifier)` for the item
    registry. Saved stacks decode through `ItemStack.CODEC` → `holderByNameCodec` → `get()`, so inventories, chests
    and item components are all covered (checked by an independent review). PR #1266.
- [x] An old waterskin could hold 5 sips; leather holds 3. Clamp the stored water to the tier's max when it's read,
  so an old full skin shows a full bar instead of 5/3.
  - `WaterskinItem.water(stack)` clamps sips to the tier max. Every read goes through it: drinking, refilling, the
    bar, the tooltip, chill/boil and tanning.
- [x] Change note `changes/kitchen/waterskin-old-id.md`: "# Old waterskins survive the tier update" and what the
  player sees (a waterskin from before 0.5.98 turns into a Leather Waterskin instead of disappearing).
  - Named `changes/kitchen/claude-waterskin-old-id.md` after the branch, with that title.
- [x] Build `kitchen/` (`./gradlew build`), open a PR, put "Not tested in game: load a pre-0.5.98 world" in it.
  Don't merge it: Andy merges.
  - Build passed. **PR #1266** (`claude/waterskin-old-id`) is open with that line, not merged.

## 2. YOUTH's new rules (`E:/hermes/SOUL.md`)

Add this section to YOUTH's SOUL.md as written (keep everything else), then restart the gateway with
`schtasks /run /tn Hermes_Gateway` (never `hermes gateway restart`).

- [x] Appended as written at the end of `E:/hermes/SOUL.md` (backup in `E:/hermes/backups/SOUL.md.2026-11-10.bak`).
  `schtasks /run` ran but did nothing: the task is set to ignore a second run while the gateway is up, and a
  managed worker was mid-run under it. Instead I ran `hermes sessions refresh-prompts`, so all 178 YOUTH sessions
  rebuild their prompt from the new SOUL.md on their next turn. A restart wouldn't have done that, since continuing
  sessions replay their stored prompt.

```
## Hard rules (Andy, 2026-11-10, after the Nov 8-10 audit)

Merging
- You merge only when Andy's message tells you to merge ("merge", "merge it", "merge 1250"). A question
  ("what is ready to merge?", "is X done?") gets a list and nothing else. When unsure, list and ask.
- Merge only the PRs he named, or the exact list you just gave him. Never a PR whose latest review asked for
  changes, whose checks are red or pending, or that a sibling PR it depends on isn't merging with.
- Never push to master. Every change, even one line, goes through a PR. Only the Release workflow writes master.

Andy's asks
- Every message from Andy gets an answer from you, even "got it, doing X now".
- Every ask becomes an Inbox task or a line in the vault's "Andy asks" list the same turn, and stays there until
  it's done or Andy drops it. An offer you make ("I can fix that") is an ask too.
- When Andy orders a change to Hermes (models, which bot writes, schedules), do it. You may name the downside once,
  in one line, then do it anyway unless he changes his mind.

Telling the truth about state
- Before you say a PR is open, merged, ready or waiting, check it that minute (`gh pr view N --json state,mergedAt`).
- "master" means origin/master after `git fetch`. A local merge commit that isn't pushed is not master.
- When something fails (a vault update, a build, a merge), say it failed. Never report it as done.

Discord
- One post when you start a job and one when it's done or blocked. No "Working — N min" heartbeats, no tool traces,
  no step-by-step narration. Model fallback notices go to the bot-alerts thread, not the conversation.
- Post in a public server channel (Tenbuki) only after Andy says yes to that exact post.

Tidiness
- After a merge, remove its worktree. Never leave a worktree mid-merge: finish or `git merge --abort`.
```

## 3. Hermes fixes Andy already asked for

- [x] **Quill is the only writer** (Andy, Nov 10 6:28 AM): take `claude-writer` out of the dispatcher's writer list
  (`E:/hermes/scripts/dispatch_bots.py`, and `E:/hermes/powerpack/vault.py` if it's listed there).
  - Removed from `BOTS` and `WRITERS` in `dispatch_bots.py` (it isn't in `vault.py`), and disabled its own job in
    `profiles/claude-writer/cron/jobs.json`. It held no Inbox claims.
- [x] **Swap YOUTH back to NVIDIA Super** (Andy asked, YOUTH declined): `E:/hermes/config.yaml`, first model.
  Keep ONE `providers:` block; check with `hermes fallback list`.
  - `model.default` is now `nemotron-3.5-super-vl-120b-a12b` and Lightning is fallback 1 (it was Super). Still one
    `providers:` block. `hermes fallback list` shows Primary Super. The gateway re-reads the config on the next
    message (the model is part of its agent cache key), so no restart is needed. Backup in `E:/hermes/backups/`.
- [x] **Prometheus counts toward the 2-bot cap**: it's coded as a "cloud" bot in `vault.py` / `dispatch_bots.py`,
  so it runs on top of the cap. It runs on the PC; count it.
  - `CLOUD_BOT = None` in both files, comments updated. Quill is still the only bot outside the cap.
- [x] **Checklists into the repo** (Andy, Nov 10 12:00 PM): commit the files in `E:/wt/coordinator-checklists` on a
  branch and open a PR (no change note needed if no mod code changed).
  - **PR #1265** adds `docs/checklists/` (4 pick-lists plus a README), on a fresh branch off origin/master. The old
    worktree sat on YOUTH's unpushed merge commit. Its `jujutsu-mod-checklist.md` was the Quill task card, so I used
    `Picklists/Jujutsu five mods pick-list.md` (the real checklist).
- [x] **Clean up**: `E:/wt/pr-1236` and `E:/wt/pr-1239` (abort their merges), `E:/wt/kanban-remove-text-pass`,
  `E:/wt/merge-1248-new`, the empty `E:/wt/odysseus-names` (`git worktree remove` each, then `git worktree prune`);
  delete the duplicate skill `E:/hermes/skills/fable-prompt` if the same skill is installed elsewhere.
  - Aborted both merges and removed all five; they were worktrees of Hermes's clone (`voice-pilot/repo`). Every
    commit in them was already on origin. Pruned. `fable-prompt` matched Quill's copy byte for byte, so I moved it
    to `E:/hermes/backups/skills/`.
- [x] **Stale vault cards**: the ape-giant task should point at #1260 (merged), not #1256 (closed): set it
  `pr=1260` and let the sync mark it done. Check whether `jujujutsu-mod-checklist` and `jujutsu-mod-checklist` are
  the same ask; if so, keep one and add the other's notes to it.
  - The Inbox card already had pr 1260 and was done. The stale line came from
    `Agents/ape-giant-trees-flying-screenshake-dust.md`, now status done and pr 1260. The two jujutsu cards were the
    same ask: kept `jujutsu-mod-checklist`, noted the other's request in it, and deleted `jujujutsu-mod-checklist`.
- [x] Tell Andy what's in `/memory pending` (3 memory batches, 1 deletion batch): he approves or drops them.
  - There are 3 batches in total, and the deletion is one of them:
    - `19dcda96`: drop "Shell is bash/MSYS…; python3/pip mismatch", and add where the bot profiles live and how to
      run another profile's cron.
    - `a8f4ab9a`: drop "tried hermes-ui, not cloned", and add "prefers questions as Discord polls".
    - `5747c1ec`: drop "tried hermes-ui, not cloned" only, the same removal as `a8f4ab9a`.
  - Told Andy in chat.

## 4. Done in the cloud

- 0.22.0: Andy chose "have the tool support a bigger bump when I ask". PR #1264 adds `bump: minor` to change notes
  (changes/README.md) and carries the 0.22.0 note. Nothing to do here.

## When you're done

Write one line per box above, run `python tools/vault_sync.py`, and tell Andy in Discord (one post).
