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
