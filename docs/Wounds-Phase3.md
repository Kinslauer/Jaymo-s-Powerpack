# Wounds phase 3: cripplings (Powers 0.14.2)

Built on phases 1 and 2 (`core/wounds`: `Wounds`, `BodyPart`, `BodyPlan`, `HitRouting`, `Downed`). Plan:
`docs/Wounds-Plan.md`, "When a part reaches zero", the arm/leg/foot/wing bullets, "Overkill", "Existing cripplings
feed in", and the Healing rule `BROKEN_CAP`.

## Broken parts

A limb part (not HEAD or BODY) that reaches 0 is **broken** (an arm or leg), **sprained** (a foot) or **grounded** (a
wing). A synced attachment `jaymospowers:wounds_broken` (int bitmask over `BodyPart.ordinal()`, persistent, synced to
all so other players see limps later; not copied on death) holds which parts are broken. It clears when the part heals
back above `BROKEN_CAP` *and* has been set (phase 4's splint) — until then, see Healing below.

Effects, refreshed server-side every 20 ticks through transient attribute modifiers (pattern: `Fractures` /
Kitchen's `Stomach.apply` with `addOrUpdateTransientModifier`, ids under `jaymospowers:wounds_*`), so they vanish
cleanly when the break heals:

| Part | Effect |
|---|---|
| Arm (one) | attack damage -30%, attack speed -25%, block break speed -30%; the shield or bow can't be used from that arm's hand (cancel use: the main hand is the right arm for right-handed players, `getMainArm()`); 25% chance (`ARM_DROP_CHANCE`) the held item in that hand drops when it breaks |
| Both arms | can't use or attack with items at all (UseItemCallback / AttackEntityCallback / AttackBlockCallback cancel, as phase 2's downed does) |
| Leg (one) | movement speed -30%, no sprinting, jump strength -40% |
| Both legs | crawl only (Powers' `Crawl`, as phase 2 forces it for the downed) |
| Foot (one) | movement speed -10%, louder steps (skip the sound part if no existing pattern) |
| Wings | flyers can't fly: for mobs that fly, set no-gravity off / drop them (simplest existing pattern; if none, give Slow Falling off and push down) |

Mobs get the same modifiers. Kitchen corpses show the breaks: add the scoreboard tags
`jaymoskitchen.broken.<part>` (Kitchen reads `jaymoskitchen.missing.*` the same way for severed parts; Kitchen-side
drawing of breaks is optional this phase, the tags are enough).

## Healing

- Natural healing (phase 1's reconcile) never lifts a broken part above `BROKEN_CAP` (0.25) until it is set; the
  surplus goes to other parts (if every hurt part is capped, the extra health is refused: clamp vanilla health so
  health still equals the parts' total).
- Phase 4 adds the splint ("set"); for now a broken part is set by: a priest (Fractures' existing cleric fee), a
  respawn, a Hermit Bean, Verdant's healing, Chrono's Bloom and Wind's Zephyr's Breath (the things that already mend
  Fractures' breaks: wire them to `Wounds.setPart`/`mendAll`). Once set, it heals normally and the break clears above
  `BROKEN_CAP`.
- `/wounds break <part> [target]` and `/wounds mend [target]` debug subcommands.

## Fractures folds in

- `core/Fractures` today breaks a limb on blunt blows (`blunt`) with its own attachments (`BROKEN`, `MENDING`,
  `MENDED_BY`) and limp/drop effects. Make Fractures a thin front for Wounds: a blunt blow that would break a bone now
  calls `Wounds.breakPart(entity, part, attacker)` (which zeroes that part's health share, overflowing to the body as
  usual, and marks it broken). Keep Fractures' head daze and the skeleton shatter as they are. Remove Fractures' own
  limp/drop effects and its BROKEN state once Wounds owns them; keep its public method names working (delegate) so
  callers elsewhere still compile. The client's hanging-arm drawing (`Dismembered`) reads Fractures' broken state:
  point it at Wounds' broken bitmask instead.

## Sever on overkill (Dismember)

- Replace Dismember's flat random sever chance: a hit on an already broken limb (or the head when it reaches 0) for
  more than `SEVER_OVERKILL` (0.5 of that part's max) with a severing weapon (Dismember's existing blade check) cuts
  it off. Dismember's blade chances (`RATE`, per-blade multipliers) become multipliers on that: the chance is
  `min(1, SEVER_BASE (0.6) * bladeMultiplier)`. A severed head is death as now. A severed limb keeps Dismember's
  `MISSING` and regrowth unchanged; its Wounds part stays at 0 and broken (no healing a missing limb).

## Existing cripplings feed in

Find every place in Powers that cripples a body with its own slowness or leg effects (Darktide's leg strikes, the
Great Ape, Ledge falls, Smoke's ash, Wind's crushes and the like: grep for MOVEMENT_SPEED modifiers, MobEffects.
SLOWNESS used as a "crippled leg", "cripple"/"leg" in names) and, where the effect is meant as an injury to a limb,
route it through `Wounds.hurtPart(entity, part, amount, by)` or `Wounds.breakPart(...)` instead. Leave effects that
are not limb injuries (slowness from a power's field, weather, a spell) alone. List each site in the report with what
you did. Do not touch Kingdoms (another thread's PR adds a Crippled effect there; it stays as is).

## Release

Powers 0.14.2, MODLOG entry, mark phase 3 in `docs/Wounds-Plan.md`. All tuning numbers as `static final` constants.
