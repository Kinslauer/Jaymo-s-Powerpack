# Jaymo's Powers: MODLOG

A superpowers mod for Minecraft Java 26.3 (Fabric). It's the centrepiece of a modpack with Jaymo's Kitchen.

## Design
- **Getting a power:**
  - **Survival:** each player's power awakens on its own after a random 1–3 Minecraft days of survival play
    (24000–72000 ticks; the timer is saved and kept through death). Hints appear 5 minutes and 1 minute out.
    The awakening shows a title, a totem sound and particles.
  - **Creative:** press **H** for the powers screen and pick any power, or remove yours.
  - **Commands** (gamemasters): `/power set <players> <wind|arsenal|web|chrono>`, `/power clear <players>`,
    `/power awaken <players>` (a random power right now), `/power get <player>`.
- **Controls:** four shared ability keys, **R, G, V and B** by default, rebindable under Controls → Powers. **H**
  opens the powers screen, which lists every power and what its keys do.
- **Powers:** Wind Control, Pocket Arsenal, Web Slinger, Chronokinesis. Each has its own file in `docs/`.

## Architecture
- `api/Power` is the server half: `onGained`, `onLost`, `tick`, `onKey`, `allowsFloating`, `skipsMovementCheck`.
  The client half is `client/PowerClient`: `tick`, `onKey`, `renderHud`, `onActiveChanged`.
- `core/Powers` holds the registry and the `jaymospowers:power` attachment. The attachment is persistent, kept on
  death, and synced to all clients, so `Powers.of(player)` works on both sides. The same class runs the awakening.
- Key presses go to the server (`KeyInput` packet, held state in `PowerInput`) and to the local `PowerClient`.
- `mixin/ServerGamePacketListenerImplMixin` stops the "floating too long" kick and the "moved too quickly"
  check while a power says so. Flight and swinging need this on dedicated servers.
- Each power lives in its own packages:
  - `power/<name>` and `client/power/<name>`
  - its own mixin configs, `jaymospowers.<name>[.client].mixins.json`
  - its own lang file, `lang/parts/<name>.json`, which `processResources` merges into `en_us.json`
- Each power has its own `init()`.

## Environment
- Same toolchain as Jaymo's Kitchen: JDK 25, `GRADLE_USER_HOME=E:\gradle-home`, Loom 1.18, Fabric API 0.161.0+26.3.
- Build output goes to `E:\jaymospowers-build\<checkout folder>`, kept out of OneDrive.
- The dev game also loads Jaymo's Kitchen (`libs/jaymos-kitchen-*.jar`), Balm and Cooking for Blockheads as
  `localRuntime`.
- The decompiled 26.3 sources are in `E:\mcsrc-26.3`.
- Launchers:
  - `Play Jaymo's Powers.bat` runs the dev client.
  - `Build Modpack.bat` gathers every jar into `modpack\mods` for CurseForge profiles and the server.

## Log
- **0.1.0, the core:**
  - Power API, registry, synced attachment, key packets, survival awakening, `/power`, powers screen, HUD badge
    and the anti-kick mixin.
  - The four powers were built in parallel by four agents, one git worktree each, then merged.
- **0.1.0, the four powers (merged):**
  - Wind (Viltrumite-style hover / cruise / sonic flight, channelled buffs).
  - Arsenal (pulls Balkon's WeaponMod: Legacy + vanilla weapons as 75 s summons, barrage, stash).
  - Web (rope-physics swing, zip, web shot, yank).
  - Chrono (rewind, stasis, overclock, time bloom).
- **The modpack:**
  - Mods: Fabric API 0.162, Jaymo's Kitchen 0.4.11, Balm, Cooking/Pantry for Blockheads, Balkon's WeaponMod: Legacy
    (+ Architectury, Cloth Config), Better Combat (+ Player Animation Library), JEI (+ MezzConfig), Nature's
    Compass, Xaero's Minimap, Sodium and Ok Zoomer. The extras were matched by file hash to the friends' server.
  - `gradlew packageModpack` (or `Build Modpack.bat`) writes the CurseForge import zip and `modpack/mods`, and
    syncs `Serverfiles/mods`.
  - Epic Fight can't be included: it is Forge/NeoForge only, up to 1.21.1.
- **Server (`Serverfiles/`, not in git; it holds the playit secret):** the old kitchen server, converted:
  - mods synced to the pack, `allow-flight=true`, a new motd and window titles
  - backups in `mods-backup-kitchen/` and `world-backup-before-powers/`
  - shares port 25565 and the playit tunnel with `Desktop\Minecraft Server`, so run only one at a time
- **Smoke tests:**
  - The dedicated server boots with 65 mods and stops cleanly.
  - The dev client loads 78 mods and joins a copy of the server world. No mixin or registration errors.
- **0.1.1:**
  - Wind: impact damage, HUD moved, hover ragdoll sway.
  - Web: two independent webs, ragdoll arm posing.
  - Chrono: HUD moved.
  - The server lives in `Desktop\Serverfiles`.
- **0.1.2:**
  - **Mastery** (`core/Mastery`):
    - A natural awakening unlocks only R. The rest unlock at 30, 90 and 180 points.
    - Points come from ability use (max one per 5 s) and from playing with the power (one per minute).
    - Creative and `/power set` give all four abilities. `/power level <players> <1-4>` sets the count directly.
  - H does nothing in survival until the power has awakened.
  - Wind realism pass and Tailwind ground gust. Web slingshot and web wings.
  - **Fall damage always applies outside creative** (user request):
    - Wind's and Web's fall waivers and Web's safe-fall passive are removed.
    - Controlled descents (Wind flight, web wings) slower than 0.5 b/t don't build fall distance, like vanilla's elytra
      rule.
    - Core: for power holders, fall distance resets while moving up, so falls are measured from the apex.
- **0.1.3:**
  - Wind: steep turn-rate falloff at speed. A snap turn at speed causes a 2 s ragdoll tumble, then recovery into a
    hover in the recovery pose (from the user's reference image).
  - Tailwind charges for up to 5 s. A full charge on the ground is a TNT-strength gust with a 180 s cooldown.
- **0.1.4:**
  - Web: charged web blast, glide lean, 2-block wing rule with no cooldown, slingshot lockout and aim toward the look.
  - `Power.unlockOrder()`: charged abilities unlock last (Wind R,V,B,G; Web R,G,B,V). `Mastery` follows each power's
    order.
- **0.1.5:**
  - Chrono: Stasis stops players too (held in place, no actions) for 3 s, and it's the last unlock.
  - Arsenal: Barrage blades land as pickupable weapons for the caster.
- **0.1.6:** Arsenal Reach Through is now animated: a portal opens, the arm reaches in, and the weapon is drawn out.
- **0.1.7:** Web Yank (B) holds its target on a web lead while held: you can drag it or let it hang, and it snaps when
  stretched or when the target fights back.
- **0.1.8:** Web Yank released while aiming at a block hitches the creature there (lead-style, barely able to move,
  breakable). Held or hitched creatures ragdoll.
