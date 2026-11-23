---
status: open
for: any
from: coordinator
asked: 2026-11-23
thread: 
claimed_by: 
pr: 
suggested_owns:
  - src/main/java/com/jaymospowers/core/finisher/Finishers.java (new D_* kinds + the landing start)
  - src/client/java/com/jaymospowers/client/finisher/FinisherPose.java (D_* keys)
  - src/client/java/com/jaymospowers/client/wounds/ExecutionReact.java (the pinned victim)
  - kingdoms/src/main/java/com/jaymoskingdoms/HiddenBlade.java (leap hands off to the drop kill)
---
# Drop assassinations: every landing on a foe is an animated attack

**Andy's words:** "Id like a drop assassination or attack. The refined one with the hidden blade needs an animation akin to this, but every player landing on an enemy should deal a devastating animated attack" (with a reference clip; stills in `Assets/drop-assassination-ref.png`, about the first 2.5 s of the clip).

**The clip, beat by beat (what the hidden-blade one should copy):**
1. Mid-air, arms flung wide, coat flaring, coming down onto the foe's back.
2. The impact drives the foe flat on their face; the attacker lands straddling their back on one knee.
3. Arms thrown out wide for balance, then the blade hand plunges down into the back/neck. The off hand pins the shoulders.
4. A beat held over the body, then the attacker rises, blade still out, and walks off. The camera stays behind and above the attacker the whole time.
About 2 seconds from landing to standing.

**What's wanted:**
- **Everyone gets a drop attack.** Any player falling at least the leap's fall (`HiddenBlade.LEAP_MIN_FALL`, 4 blocks) onto a living foe plays a finisher-style move on landing, with no hold and no perk needed: the foe is slammed to the ground, the move plays, and the player takes no fall damage. One move per weapon kind, read the same way `Finishers.kind(ItemStack)` reads weapons, for example:
  - blade: driven down through the back while pinned;
  - blunt or axe: brought down two-handed on the back of the head as you land;
  - spear: landed point-first, pinning them;
  - bare hands: a knee into the back and the head slammed into the ground.
- **The hidden blade's is the refined one**: the clip's move above, as a new `D_HIDDEN_BLADE` kind. It keeps what the leap already does (fall damage dealt as your blow, crimes, witnesses, kill credit, Stealth XP); the animation takes over from the current instant hit. Unaware or Crippled foes die on the plunge, the way the 10x stab works now. An aware, healthy foe is pinned and wounded, not one-shot.
- **Devastating, but it follows the pack's rules.** Humanoids go down, not die, unless the move is a kill (pack rule: downed unless beheaded or gibbed). Bosses and anything too big for Bigger Game (`STEALTH_HEALTH/HEIGHT/WIDTH`) aren't pinned: they take a heavy landing blow with a short animation and keep fighting. No discovery chat text, so cues are sound and the existing overlay lines only.
- **The victim reacts.** They're flattened face-down for the move (borrow `DownedPose`'s prone lie), with `ExecutionReact` jolts on each beat, instead of standing through it.
- **Camera:** your own drop attack is watched from behind and above, like every finisher (`FinisherPose.CINEMATIC`). No FOV change. You're invulnerable while it plays (cutscene rule).

**Where to start:**
- `kingdoms/.../HiddenBlade.java`: `falling()` / `allowDamage()` / `leap()` already catch a landing on a living thing (swept box plus a landing fallback). Generalise that catch to any player, then hand it to Powers' `Finishers` to start a `D_*` kind on that body. Kingdoms only adds the hidden blade's extras (Crippled, Stealth XP, the leap's damage).
- `Finishers.java`: `Kind` (add the `D_*` kinds with their lengths and beats), `begin()` / `plays()` / `blow()` (the move runs itself once begun, rooted, blows on beats), `stealthKillable()` for the size limits, and `STEALTH_KILL_TAG` so Kingdoms' `StealthKills` counts hidden-blade drop kills as assassinations.
- `FinisherPose.java`: the `KEYS` table (sink, bend, arms, legs, head, grip per tick). `S_BLADE`'s keys are the nearest to start from; the drop move needs a sink to a straddling kneel on landing tick 0 and the arms-wide balance pose. Note `STEALTH_REACH`: the drop kill wants ~0 reach, standing on them.
- `ExecutionReact.java`, `DownedPose.java`: the victim.
- `HiddenBladeClient.java`: the blade has to show out during the move (the leap springs it on landing).

## Notes
- Lore: it's the Stealth tree's art, the hidden blade's. Add a `(new)` paragraph to Lore.md placing the drop assassination with whoever the hidden blade's makers are in canon.
- Check every new pose in game; say plainly which ones haven't been seen.
- Open question for Andy if it matters to the builder: should the plain (non-hidden-blade) drop attack need any skill at all, or truly every player? Built as "every player" per his words.
