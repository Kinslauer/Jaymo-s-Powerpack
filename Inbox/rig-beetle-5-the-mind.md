---
status: in-review
for: any
from: coordinator (Zemo Baki cross-reference thread)
asked: 2026-11-18
thread: https://claude.ai/code/project/chan_01638ouFWZKwEZ1ET74kBk9B?thread=cmsg_01638ouFWZKwEZ1ET74kBk9BX7wxqnAirwqSZbVfgHyi5g
claimed_by: rig-beetle-5-the-mind
pr: 1774
suggested_owns:
  - src/main/java/com/jaymospowers/rig/RigTalk.java (new)
  - src/main/java/com/jaymospowers/rig/RigPilot.java (new)
  - src/main/java/com/jaymospowers/rig/RigEvac.java (new)
  - RigMind.java (lines only)
  - changes/powers/<branch>.md
---
# Rig patch 5: the Mind talks, flies and keeps watch

**Andy's words:** "all done, you can split it and add it to the vault" (after ticking these rows on the Rig pick list).

**What's wanted:** a Rig wearer gets these, each a Rig menu entry with charge cost, poses and sounds:

- **Talk to the Mind in chat** (`md-talk`): Type plain words and it answers or acts: 'light up the area', 'dig me out', 'how do I make a furnace'. _Our side:_ A set of command phrases the Mind catches in your own chat (only you see the reply), answered in its personality. It never explains the lore, only hints (your standing rule).
- **Autopilot** (`md-pilot`): 'Fly me to the nearest village' and the suit flies you there. _Our side:_ The Mind flies the suit along a route to a place it knows (villages, your bed, a marked spot on the wrist table), charge permitting. Any key takes control back.
- **Threat calls** (`md-threat`): Each target gets a one-line verdict on the visor: 'ignore, for now', 'kill it', 'melt it'. _Our side:_ The helm brackets and the target card exist. Add the Mind's short verdict, worded by its personality.
- **A spoken Mind** (`md-voice`): The AI speaks every line out loud. _Our side:_ The Mind talks in text now. Voicing it means recorded lines; your VoiceStudio note says its default voice is non-commercial, so this needs a voice source you're happy with first.
- **Evacuate the host** (`md-evac`): When the suit is about to break, it throws you clear. _Our side:_ Under a set armour wear with the Core near empty, the suit ejects you upward and slumps down as a wreck you can repair at the Forge.
- **The Mind fights while you're down** (`md-downed`): If you go down, the suit fights on for you. _Our side:_ The takeover save works before you drop. While Downed, the Mind could keep firing the palm blast at whatever's on you until help comes.
- **Combat log and vitals** (`md-log`): A small log of hits given and taken, and your body's state, on the visor. _Our side:_ A fold-out panel on RigHud: the last few blows, and your limbs' state from Wounds (broken, bleeding).

**Where to start:** Chat commands: phrases the Mind catches in the wearer's own chat (the message isn't broadcast), answered privately in its Personality. It never explains lore, only oblique hints (Andy's rule). Autopilot: to a village, your bed or a wrist-table mark, any key takes control back. Downed (Downed class): the Mind keeps firing the palm at what's on you. Combat log + limb state from Wounds on RigHud. **md-voice is blocked on a voice source**: see voicestudio-voices memory (OmniVoice default is non-commercial); build the hooks (a line id per say()) and ask Andy which voice before recording anything.

## Shared rules for every rig-beetle patch
- Source: Andy's picks (2026-11-18) from the Zemo&Baki "DC Universe" jar, which is one Blue Beetle armour mod. **All Rights Reserved: ideas only.** Own code, own poses, own art, no DC names (no scarab/Khaji-Da/Kord/Bug). Pick list: https://claude.ai/artifact/4hWigMxitF3zRsp1JYQsVX (db `picks/andy`). Reference dump on Andy's PC: `Mod Testing\zemo-dc\inventory.md` (names and text only).
- Everything is **Frostborn Rig tech**: plates slide, lock and fold; nothing grows or is alive (the symbiote's grown weapon arm and tendrils stay the symbiote's).
- New actives are **Rig menu entries** (RigMenuScreen / RigPicks pattern), so J or a no-power key can be set to them; don't invent new keybinds. Gate the stronger ones by Mind level (RigLevels) and whole set where it makes sense; every one costs Core charge.
- Poses go through RigBeats / RigPose (three beats: wind-up, snap with overshoot, settle) and RigSuit draws the plates moving into place. Effects through MagicFx only. Tuning numbers as `static final` at the top of each class.
- Files several patches touch: `RigActives`, `RigPose`, `RigSuit`, `RigClient`, `RigHud`, `RigMenuScreen`/`RigPicks`. Add your own class and keep your edits there to a few hook lines; rebase before PR.
- Run 2 patches at a time. Suggested pairs: (1 arm forms + 5 the Mind), (3 back rig + 6 senses and nova), (2 guns + 4 flight).
- Lore first: read `Lore.md` (the Frostborn rigs, Los Perrito's troopers). Anything needing a new line gets a `(new)` paragraph, quoted in full to Andy for approval.
- One PR per patch with `changes/powers/<branch>.md`; never touch `version=`, MODLOGs or codemap. Andy tests by merging.

## Notes
- 2026-11-18 16:23 hermes-b's claim had no sign of work for 30 min: handed back as open.
- 2026-11-18 16:55 PC Claude took it on branch claude/rig-beetle-5-mind (an older E:/wt/rig-beetle-5-the-mind worktree was left from a stalled run, no commits; left untouched).
- 2026-11-18 17:09 Question for Andy: md-voice needs a voice source you're happy with (VoiceStudio's default is non-commercial). Which voice? Everything else is in PR #1773.
- 2026-11-18 19:44 Andy is working on the voices (Google TTS, #1780); md-voice waits on that.
- 2026-11-18 21:42 Review: pr: 1774 was a closed duplicate (CONFLICTING, never merged); the work landed as PR #1773, merged 2026-11-18. Pointed pr: at 1773 so the sync files it as done. md-voice still waits on Andy's voices (#1780).
