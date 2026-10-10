# 0.8.0 brief: the playtest patch

You are one of the agents building **Jaymo's Powers 0.8.0**, a Fabric mod for Minecraft Java **26.3**, in a Linux
cloud container. Each agent owns one track of `docs/Plan-0.8.md`, works in its own git worktree, and commits there.
The lead merges the branches afterwards, so **stay inside your track's files**.

Read first: `CLAUDE.md`, `docs/HANDOFF.md` (26.3 API facts, the user's preferences), your track in
`docs/Plan-0.8.md`, and `docs/<power>.md`. The code already does a lot: read the classes you're changing and build
on them. Don't rewrite.

## Environment
- Build from your worktree root:
  ```
  export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64; sh ./gradlew build --console=plain
  ```
  It must end with `BUILD SUCCESSFUL`. Gradle's cache is already warm.
- **Do not run `runClient`, `runServer` or `packageModpack`.** You can't playtest; the user's tester will.
- 26.3 uses Mojang names with no mappings. **Check every Minecraft API you use** against the real sources before
  using it: the Minecraft jars are in `~/.gradle/caches/` (find them with
  `find ~/.gradle/caches -name '*minecraft*26.3*.jar'`); list classes with `unzip -l` and read signatures with
  `javap -cp <jar> <class>`. Grep the existing code for working examples first: most APIs you need are already used
  somewhere in this repo.
- Edit Java with the Edit tool or small Python scripts that assert each replaced snippet occurs once. Files use tabs.

## Rules
1. **Only touch the files your track lists** (plus new files in your own packages). Never edit `build.gradle`,
   `fabric.mod.json`, `JaymosPowers.java` or other powers' files unless your track says so. If you need something
   outside your files, work around it and say so in your report.
2. New registry, packet and attachment ids start with your power's prefix (`gravity_`, `web_`, ...). Any new class
   that holds an `AttachmentType` must be listed in your report, because the lead has to add it to
   `JaymosPowers.registerAttachments()` (dedicated servers break otherwise). If your track owns
   `JaymosPowers.java`, add it yourself.
3. Player-facing text goes in `src/main/resources/assets/jaymospowers/lang/parts/<power>.json` only.
4. Code under `src/main` must never reference `net.minecraft.client.*`. Client code goes in `src/client/java/...`.
5. Multiplayer must work on a dedicated server: the server decides damage, cooldowns and effects; other players
   must see your effects (particles via the server, synced attachments or packets).
6. Every tuning number goes in a named `static final` constant at the top of its class.
7. Fall damage always applies outside creative unless the plan says otherwise.
8. Original art only (Python + Pillow, `tools/gen_<power>.py`); vanilla sounds and particles.
9. Update `docs/<power>.md` for what you changed.
10. When it builds cleanly, commit everything in your worktree:
    ```
    git add -A && git -c user.name="toopo" -c user.email="toopoint09@gmail.com" commit -q -m "<Track>: <summary>" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
    ```
11. **Final report** (your last message, concise): branch and commit hash; what you built per plan item; anything
    you skipped or changed from the plan and why; new attachment classes; what's risky at runtime and the tester
    should check first.
