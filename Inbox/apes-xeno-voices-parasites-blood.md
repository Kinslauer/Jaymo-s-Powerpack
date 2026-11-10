---
status: open
for: hermes
from: Andy (Claude chat, 2026-11-09 10:45 PM)
asked: 2026-11-09
claimed_by: 
pr: 
---

# Great apes, xeno voices, clearing parasites, still blood

## What's wanted

Andy, after playing tonight (with four video clips, see below):

1. **Another way to clear parasites.** Today there isn't enough of one: add another method a player can actually reach.
2. **Great apes get stuck and can't hear anyone on the ground when they transform.** Fix them getting stuck, and make
   sure a transformed great ape still hears the players on the ground.
3. **Titan voice changer for big forms.** A transformed great ape, and every xeno form, gets a titan voice changer
   turned on (a deep, huge voice in Simple Voice Chat), and is audible to everyone on the ground: their voice range
   must reach players far below them.
4. **Blood stays still.** Blood particles on the ground keep re-animating every few seconds; once they've landed they
   should stay static.

## Clips

Andy's Medal clips from 10:19 to 10:35 PM, Nov 9 (on his PC at `E:\Medal\Clips\Minecraft\`):
- MedalTVMinecraft20261109221931865.mp4 (10:19 PM)
- MedalTVMinecraft20261109222132195.mp4 (10:21 PM)
- MedalTVMinecraft20261109223153885.mp4 (10:31 PM)
- MedalTVMinecraft20261109223521344.mp4 (10:35 PM)

They're private (not on GitHub): uploaded to Andy's Anthropic account. `run.py task` attaches every `file_...` id in
this section to the cloud session, mounted at `/mnt/session/uploads/clips/<name>`:
- `file_01YBbJv4jXkKbaGJjhNoNEP6` MedalTVMinecraft20261109221931865.mp4
- `file_011Ys47HjXF8jbAjyWyKdqEe` MedalTVMinecraft20261109222132195.mp4
- `file_014NbAYD8XnYjWaRzdaA9qwk` MedalTVMinecraft20261109223153885.mp4
- `file_01QZZFWtCQ2itkhSVms2ebYD` MedalTVMinecraft20261109223521344.mp4

To look at them, pull frames with ffmpeg (install it in the sandbox) into /tmp and read the images. He's putting more videos and pictures together for
this task; check this note for new links before starting. Watch them before deciding what "stuck" and "re-animating"
look like.

## Notes

- Voice: Simple Voice Chat is in the pack; use its server API through a compat class that checks it's loaded (as
  CLAUDE.md says for optional mods). Check what voice changing it supports before promising a "titan" effect; say
  plainly if it needs a client-side voice effect we must add ourselves.
- Blood: the blood pools/decals (PR #1169 and the drag trails) are the likely source of the re-animating.
- One PR per item is fine if they touch different files. Don't merge.
- 2026-11-10 05:25 Andy, 2026-11-10: the API credits ran out, so Hermes finishes this. Continue from the cloud agent's pushed branch cloud/apes-xeno-voices-parasites-blood (git fetch, worktree from origin/cloud/apes-xeno-voices-parasites-blood), don't start over; the cloud agent said Kingdoms had a duplicate lang key from master (fixed on master by #1243, so merge origin/master first).
