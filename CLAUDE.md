# Jaymo's Powers (and Kingdoms, Kitchen)

Minecraft 26.3 Fabric mods for the user's friends' modpack. **Read `docs/HANDOFF.md` first**: what each folder is,
how to build, hard-won 26.3 API facts, the user's preferences, and what to do next.

- Root: Jaymo's Powers (`./gradlew build`). `kingdoms/`: Jaymo's Kingdoms. `kitchen/`: Jaymo's Kitchen. Each is its
  own Gradle project (JDK 25).
- Check every Minecraft API against the 26.3 sources (`./gradlew genSources`) before using it.
- Keep each power in its own package and `lang/parts/<power>.json`; list every class that holds an attachment in
  `JaymosPowers.registerAttachments()`.
- Bump the version on every release. Never commit `Serverfiles/` (it holds a secret).
- Build to check work; don't launch long dev client/server runs unless the user asks.
- Next Powers patch: `docs/Plan-0.8.md` (4 tracks: Gravity, Arsenal, Chrono, Web; open questions at the end).
- Default keys: R/G/V/B = abilities 1-4, C = ultimate, Z = mode, H = menu, ' = costume mask. "G+B" means G, then B (e.g. Crush
  released inside your own Gravity Well).
- Tuning numbers live in `static final` constants at the top of each class; change balance there.
- Optional mods (Kitchen, WeaponMod) only through a compat class that checks they're loaded (`KitchenCompat`).
  Don't copy other mods' code or art unless their license allows it.
- The symbiote never feeds on anything living: kill, grab with tendrils, carry as a corpse, then feed. Every
  autonomous symbiote action affects the player's screen.
- Big patches run as parallel agent tracks, one per power, so they don't edit the same files; 2 at a time, since
  4 at once hit usage limits.
- Workflows: read-only scan stages use the `scanner` agent (Haiku 5.5); edit stages use the `editor` agent
  (Sonnet 5.5), each in its own worktree.
- Every finding gets an independent verifier agent before it's reported. A fix loop stops after two rounds
  with no progress.
