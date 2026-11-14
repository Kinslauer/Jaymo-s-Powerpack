# Plan: YOUTH in the world, a Sylvan wisp that explains our mechanics

Written Nov 13, 2026 by the cloud thread "Plan Minecraft server bot for Hermes". Plan only: nothing is built yet.
Replaces the "not possible" verdict on the vault task `Inbox/minecraft-server-bot-experiment.md` (Tiv's ask, Nov 12).

## What Andy asked for

- Nov 13, 4:57 PM: "I'd like a body that can spawn and relay information about any mechanic to players."
- Nov 13, 5:13 PM: "the youth bot should be a wispy winged sylvan animating in the world near where you asked, doing
  some variation of 8 gecko libbed idle animations."

So: a player asks YOUTH something in chat; a small, wispy, winged Sylvan appears beside them, idles through 8 GeckoLib
animations while it's there, and answers in chat from our own files. Hermes (YOUTH on the PC) does the thinking; the
mod does the body.

## The short answer

- **The body is our own mob, not a fake player.** A GeckoLib entity in Jaymo's Kingdoms, made the way the barrow
  beetle is (`docs/AI-HANDBOOK.md`, "Adding a GeckoLib mob to Kingdoms"). It needs no Minecraft account, no login and
  no extra server mod, and everyone already has GeckoLib in the pack. This replaces the Carpet fake player from the
  first version of this plan: a Carpet player can only look like a player skin and can't play GeckoLib animations.
- **Mineflayer stays out.** Official Mineflayer and node-minecraft-protocol stop at Minecraft 26.1 (26.3 only in
  unofficial forks), and our modded Fabric server would very likely reject it anyway. Nothing here needs it.
- **Hermes drives the body through the server console.** A few `/youth ...` commands added by the mod, sent over RCON
  with the Deck's existing `deck/rcon.py`, over the PC's existing SSH link to the Deck. Chat comes back from the server
  log. Hermes never logs in to anything.

```
player types "youth, how does Crush work?"
   -> server log (Deck) --ssh tail--> E:/hermes/scripts/mcbot.py listen --> YOUTH (Hermes, PC) reads our files
   <- /youth summon Tiv ; /youth say Tiv "<answer>"  <--ssh + rcon.py--  mcbot.py summon/say
wisp appears beside Tiv, faces them, plays "talk", answers in chat, idles, fades away later
```

## Lore placement (approved by Andy Nov 13, 5:34 PM; in Lore.md)

Lore.md has the **Sylvan** as light-footed, long-lived woodland kin, the smallest of the bloods, whose elders remember
old trees as saplings, and a Sylvan carries the season about them (`SeasonAura`). Proposed:

> (new) **The wood-wisps.** The Sylvan elders live so long that they forget nothing, and they send what they know out
> on the wind. A wood-wisp is a little of an elder's memory given wings: a small, half-there Sylvan shape that comes
> when someone says its name aloud and asks a question in earnest. It knows the workings of the world (the powers,
> the kingdoms, the kitchen) because the elders watched them made. It cannot fight, cannot be hurt, and goes back to
> the trees when nobody is asking. Folk call the one that answers them **Youth**, because it always looks young.

If Andy prefers YOUTH to stay out-of-world, the same mob can simply not be named in lore; the plan doesn't change.

## The wisp (mod side, Jaymo's Kingdoms)

**Look.** Sylvan-sized (smaller than a player, about 0.9 blocks tall), slender, translucent and glowing faintly
(emissive layer), with two pairs of thin, veined fae wings and a trailing wisp of light below the waist instead of
legs, plus the Sylvan sprigs from `BeastParts`. A light tint of the current season (spring green, summer gold, autumn
russet, winter pale blue) if Seasons are on. New Blockbench model `kingdoms/art/youth_wisp.bbmodel`, written to the
game with `tools/bbmodel_geckolib.py` (handbook step 1). Prometheus (hermes-modeller) or the 3D Modeler Agent can
draft the model; Andy approves the look from a `bbmodel_preview.py` render before Java.

**Animations (GeckoLib, `youth_wisp.animation.json`).** 8 looping idles plus 3 action animations:

| # | Idle | What it does |
|---|---|---|
| 1 | `idle.hover` | Gentle bob, wings in a slow beat. The default, most common. |
| 2 | `idle.flutter` | Quick wing burst, rises half a block, settles. |
| 3 | `idle.loop` | A small loop-the-loop around its own height. |
| 4 | `idle.sit` | Sits cross-legged on the air, wings folded, chin on hand. |
| 5 | `idle.stretch` | Arms and wings stretch wide, a yawn. |
| 6 | `idle.peer` | Leans in toward the nearest player, head tilted, curious. |
| 7 | `idle.twirl` | A slow pirouette with the wisp-trail spiralling. |
| 8 | `idle.cup` | Cups its hands; a few season motes rise from them and drift off. |
| - | `appear` / `vanish` | Unfolds out of a swirl of motes / folds back into them (about 1 s each). |
| - | `talk` | Hand gestures and a brighter glow while a chat answer is being sent. |

Variation: the "idle" controller picks the next idle when the current one ends, weighted (hover 3x, the rest 1x),
never the same one twice in a row, with a 2-6 second hover between picks. Each wisp seeds its own random so two wisps
side by side don't move in step. `talk`, `appear` and `vanish` are triggerable animations started by the server.
Particles are typed `MagicFx` motes and sparks (never recoloured redstone dust), tinted by season.

**Behaviour.**
- Flies, no gravity, no collisions with players, can't be hurt, pushed or targeted by mobs, drops nothing, never
  saved to disk (it's gone after a restart).
- Appears 2 blocks in front of and a little above the asker, in the nearest open air spot (falls back to above their
  head in tight caves). It turns to face the asker; while idle it drifts within 2 blocks of where it appeared and
  turns to whichever player is nearest.
- If the asker walks off more than 16 blocks, it vanishes and reappears beside them next time they ask.
- It fades away (`vanish`) after 2 minutes with no question, on `/youth dismiss`, or when its asker logs off.
- At most one wisp per asker and 4 in the world at once.
- Right-clicking it shows "Ask me in chat: youth, how does ... work?" so players learn how to use it.

**Commands (op level 2; RCON has it, normal players don't).**

| Command | What it does |
|---|---|
| `/youth summon <player>` | Brings that player's wisp in beside them (plays `appear`), or moves it there. |
| `/youth say <player> <text>` | The wisp plays `talk` and the line goes to that player as `[Youth] text` (light green). |
| `/youth say @all <text>` | Same, to everyone (only when the asker wrote "youth, tell everyone"). |
| `/youth dismiss <player>` | That player's wisp vanishes. |
| `/youth status` | One line of JSON: online players, their positions and dimensions, which have a wisp. |

New code: `kingdoms/src/main/java/com/jaymoskingdoms/inhabitants/YouthWisp.java` (entity), its renderer in
`kingdoms/src/client/...`, `YouthCommand.java`, and lang in `lang/parts/youth_wisp.json`. Tuning numbers (sizes,
timeouts, caps, distances) as `static final` constants at the top of the class. One PR to the repo with a
`changes/kingdoms/<branch>.md` note; merge only on Andy's word.

## The brain (PC side, Hermes)

**`E:/hermes/scripts/mcbot.py`**, the only way Hermes touches the server. Fixed verbs only:

| Verb | Sends |
|---|---|
| `listen` | Tails `~/jaymos-server/logs/latest.log` over SSH; prints chat lines that start with or mention "youth", plus joins and leaves. Sends nothing. |
| `summon <player>` | `/youth summon <player>` |
| `say <player> <text>` / `say-all <text>` | `/youth say <player> <text>` (plain text; the script strips newlines, `§` and `/` at the start, the mod strips formatting again) |
| `dismiss <player>` | `/youth dismiss <player>` |
| `status` | `/youth status` |
| `off` / `on` | Kill switch: a flag file that makes every verb refuse until `on`. |

**The listener** (a scheduled task like the gateway, or a 1-minute cron) runs `mcbot.py listen`. For each mention it
runs `mcbot.py summon <asker>` at once (so the wisp appears while YOUTH thinks), then hands the question to YOUTH with
`hermes chat -q ... --oneshot`. YOUTH answers with `mcbot.py say <asker> ...`. "youth, bye" goes straight to
`dismiss` without asking YOUTH.

**Where the answers come from.** YOUTH must answer from our own files, never from memory or general Minecraft
knowledge (the free models make up mod mechanics otherwise). A YOUTH skill `minecraft-mechanics` tells it to search
the PC checkout `E:/Origins Mod` (pulled once a day) in this order:

1. A pre-built `mechanics-cheatsheet.md` (one paragraph per power and system), made by PC Claude from the sources below
   and rebuilt after each release.
2. `docs/Abilities.md`, the MODLOGs (`MODLOG.md`, `kingdoms/MODLOG.md`, `kitchen/MODLOG.md`) and `docs/modlog/`.
3. Lang files (`src/main/resources/assets/*/lang/`, Kingdoms `lang/parts/*.json`): item, ability and tooltip text.
4. `docs/codemap/*.md`, then the class itself, for exact numbers (tuning constants sit at the top of each class).
   Default keys: R/G/V/B abilities 1-4, C ultimate, Z mode, H menu.
5. Lore questions: the vault `Lore.md`.

`docs/*-Plan.md` files describe plans, some never built: only use them if a MODLOG or the code confirms the thing
exists. If nothing confirms an answer, YOUTH says "I'm not sure, ask Andy". Answers are 1-3 short lines, in the wisp's
voice (light, kind, a little old for its face), no Markdown.

## Safety limits

RCON is the full server console with operator power, so:

- **No raw commands.** `mcbot.py` only builds commands from its verb table. YOUTH's text only ever goes in as the last argument of
  `/youth say`, so it can't become another command. Player names must match `^[A-Za-z0-9_]{3,16}$`; no `@` selectors from YOUTH except
  the fixed `say-all`; `§` codes stripped; 200 characters per line, 3 lines per answer.
- **Rate limits in the script** (not in YOUTH's rules): 1 command a second, 1 question per player per 10 seconds,
  10 answer lines a minute overall.
- **The mod enforces its own caps** (one wisp per player, 4 total, 2-minute timeout), so even a broken script can't
  fill the world with wisps.
- **Never stop or restart the server** from `mcbot.py`; that stays with pbdr.
- **No keys anywhere.** SSH uses the PC key already on the Deck (`deck/enable-ssh.sh`); the RCON password stays in the
  Deck's `server.properties`; RCON is not opened to the network.

## Steps

**Andy:**
1. Done: body that spawns and explains mechanics; wispy winged Sylvan with 8 GeckoLib idles (Nov 13).
2. Approve (or change) the wood-wisp lore paragraph above.
3. Approve the wisp's look from a preview render before the Java is written.
4. Check `enable-rcon=true` is in the Deck's `server.properties` (pbdr already uses `rcon.py`, so it probably is).

**Mod: built in PR #1467 (Nov 13), waiting for Andy's merge. Model, 11 animations, entity, `/youth` commands; builds, dev server boots. Not seen in game yet.**
1. Model and the 11 animations in `kingdoms/art/youth_wisp.bbmodel`; preview renders for Andy.
2. `YouthWisp` entity, renderer, idle picker, `/youth` commands, lang part, change note.
3. Build, then a short test on the **Mod Testing** server: `/youth summon <you>`, watch all 8 idles, `/youth say`,
   timeout, caps.
4. PR; merge on Andy's word; pbdr puts it on the Deck.

**PC Claude (Hermes side, can start before the mod is merged by testing with plain `tellraw`):**
1. Check the PC reaches the Deck: `ssh <deck> "cd ~/jaymos-server && ./rcon.py list"`.
2. Write `mcbot.py` with the verbs and limits above.
3. Write `mechanics-cheatsheet.md` and the YOUTH skill `minecraft-mechanics`; add a SOUL rule (in game chat answer only
   people who name YOUTH, 1-3 short lines, server commands only through `mcbot.py`).
4. Test on Discord first with 10 real questions (two per power, plus Kingdoms and Kitchen); check every answer against
   the files.
5. Run the listener; test in game with Andy: "youth, how does Crush work?". Then add the lore paragraph to Lore.md if
   approved, update the vault task, and tell Andy in 5 lines.

## Response time in game

The wisp appears within about 1-2 seconds of the question (the listener reads the log and sends `summon` before
YOUTH starts thinking). The answer follows in roughly 5-20 seconds on NVIDIA Super, longer if a fallback model is
used; the wisp plays `talk` while waiting so the player knows it heard. The hours below are build time, not this.

## Rough effort (build time, once)

- Wisp model and 11 animations: 1-2 days of modelling (the biggest part; quality depends on the modeller).
- Entity, idle picker, commands: about half a day, plus a test on Mod Testing.
- `mcbot.py`, listener, skill, cheatsheet, 10-question check: 6-10 hours of PC Claude work.
- Total: about 3-4 working days spread over a week, plus two in-game tests with Andy.

## Sources checked (Nov 13, 2026)

- [mineflayer issue #4189](https://github.com/PrismarineJS/mineflayer/issues/4189): 26.3 servers kick Mineflayer bots;
  minecraft-data and node-minecraft-protocol top out at 26.1.
- [Fork PR: node-minecraft-protocol 26.3](https://github.com/Misty4119/node-minecraft-protocol/pull/1),
  [fork PR: mineflayer 26.3](https://github.com/Misty4119/mineflayer/pull/1): 26.3 only in unofficial forks.
- Repo: `docs/AI-HANDBOOK.md` ("Adding a GeckoLib mob to Kingdoms", GeckoLib 5.5.7, barrow beetle as the example),
  `BeastParts` (Sylvan fae wings and sprigs), `SeasonAura`, `deck/rcon.py`, `deck/enable-ssh.sh`, pbdr skill.
- Lore.md (vault-live): Sylvan are light-footed, long-lived woodland kin, the smallest blood.
