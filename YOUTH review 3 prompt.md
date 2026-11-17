# YOUTH review 3 (for Claude Code on Andy's PC)

Andy, Nov 17 11:19 AM: "Can we do a youth review?" The cloud thread can't read Discord, so you do it. Do it the same
way as `YOUTH report 2.md`: read YOUTH's messages with the bot token (`KANBAN_BOT_TOKEN`; never print it), check
GitHub and Hermes's own logs (`state.db` for YOUTH and every bot profile), and use a second agent to check each
finding before you write it down.

**Period:** Nov 11 4:45 PM to now (where report 2 stopped). Channels: all of parthenon (Dashboard, Notes, PR Merges,
Patch Notes, Bot alerts, Kanban, Tasks and the rest), Tenbuki, YOUTH's DMs if the API allows, and in-game chat
(the wisp, `mcbot.py` logs).

## Check

1. **The Hard rules in SOUL.md**, one table row each, as in report 2 with a third column:
   - merge only on "merge";
   - never push to master;
   - answer every ask;
   - true state (PRs, background jobs, read-aloud);
   - no heartbeats or "Let me check…";
   - public posts only on a yes;
   - do Andy's Hermes orders;
   - read exactly the part asked for.
2. **What was fixed since report 2, and whether each fix held:**
   - the bot stop list (`dispatch_bots.py --stop`): is Hephaestus still stopped, and has any stopped bot run?
   - heartbeats off, and the home channel in Bot alerts (nothing new in Tenbuki > lair);
   - missed-message backfill: anything lost since Nov 11?
   - Discord threads (`tools/discord_thread.py`): when asked for a thread, does YOUTH make a Discord thread?
   - the new patch-notes format (short, player-facing; Andy said Nov 12 they were too verbose);
   - model fallbacks (no Ollama; AMD Token Lab last): any run that failed on every model?
3. **The new in-game YOUTH** (wisp, Nov 13-17): does it answer players correctly and stay quiet when not named? Does
   HELP teleport work? Does it say anything wrong about mechanics?
4. **Bots:** what each one claimed, finished or left stuck since Nov 11. Look for false "pushed" or "done", and
   worktrees left mid-merge.
5. **Andy's asks** to YOUTH since Nov 11: list each with done / dropped / half-done.

## Write

`YOUTH report 3.md` in the vault, in the same shape as report 2:
- the comparison table across reports 1, 2 and 3;
- "What went wrong";
- "Fixed today", with a backup of every Hermes file you change (as before);
- "Needs Andy".

Fix what's clearly in scope (SOUL.md lines, config, scripts). Ask Andy before anything that can't be undone or that
posts publicly. Then run `python tools/vault_sync.py` and tell Andy in five lines.
