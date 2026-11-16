# Tester pack (auto-updating, packwiz)

Testers no longer need a new zip or jar after every merge. Their Prism Launcher instance runs
[packwiz](https://packwiz.infra.link/) before each launch. It downloads only what changed since the last launch and
removes mods that left the pack.

## For testers: set it up once

1. Install [Prism Launcher](https://prismlauncher.org/download/) (free) and sign in with your Microsoft account.
2. In Prism: **Add Instance → Import**, paste this link into the box, then press **OK**:
   `https://github.com/Kinslauer/jaymo-s-powerpack/releases/download/tester-pack-files/Jaymos-Powers-Prism.zip`
3. Launch the new **Jaymo's Powers** instance. If Prism asks for Java, let it download Java 25.
   The first launch shows a packwiz window while the whole pack downloads (a few hundred MB). Later launches only fetch
   what changed, so they take a few seconds.
   If packwiz says some CurseForge files must be downloaded by hand (some authors don't let launchers fetch their
   files), follow its window: it links each file's page and picks the files up once you've downloaded them. That's
   needed once per file version.

That's it. Each time you press Launch you're on the newest master build.

- The instance gets 8 GB of memory. Change it under **Edit → Settings → Java** if your PC has less (6 GB is the least
  that runs well).
- If a launch fails with "packwiz-installer" errors, launch again (GitHub can be slow for a minute right after a new
  build is published). If it keeps failing, send the instance's `logs/latest.log`.
- Your options, worlds and keybinds stay in this instance. To keep the ones from your CurseForge profile, copy its
  `options.txt` into the instance's `.minecraft` folder (Prism: right-click the instance → **Folder**).
- Your own extra mods (a minimap, say) can go in `.minecraft/mods` as usual. packwiz leaves files it didn't put there
  alone.

## How it stays up to date

- `.github/workflows/tester-pack.yml` runs after every Release run, so after every merge once the version bumps are in.
  It runs `gradlew packwizList` (builds Powers, Kingdoms, Kitchen and the ports, and lists every pack jar with the side
  its `fabric.mod.json` gives it), then `tools/packwiz_pack.py`, which writes the pack.
- The pack's text files (`pack.toml`, `index.toml`, `mods/*.pw.toml`, ...) live on the `tester-pack` branch, rewritten
  from scratch each run. Testers' instances read
  `https://raw.githubusercontent.com/Kinslauer/jaymo-s-powerpack/tester-pack/pack.toml`.
- Modrinth mods download from Modrinth itself (matched by file hash, so it's exactly the jar the build used). Resource
  packs, shader packs and CurseForge-only mods are fetched through CurseForge by their ids, like the CurseForge import.
  No third-party jar is ever re-uploaded by us: a jar Modrinth doesn't know fails the run.
- Our jars and our ports (Powers, Kingdoms, Kitchen, MCA's 26.3 build, TRMT, Dynamic Waters) are assets of the
  `tester-pack-files` release, named by their contents. A jar whose code didn't change keeps its asset, so testers
  don't re-download it. Assets neither the new pack nor the previous one uses are deleted.
- It reads the same mod lists as Build Modpack (`packMods`, `packModsNoCf`, `packModsOverride`, `serverOnlyMods`,
  `localPackMods`, `packResourcePacks`, `packShaderPacks` in `build.gradle`), so adding a mod there adds it here too.
- Run it by hand: GitHub → Actions → **Tester pack** → **Run workflow**. Dry run locally (uploads nothing):
  `sh ./gradlew packwizList && python3 tools/packwiz_pack.py build/packwiz-list.json --out build/tester-pack`.

## What it doesn't do

- **Fabric Loader or Minecraft version changes.** packwiz only manages files. When `loader_version` or
  `minecraft_version` changes, testers change it in Prism (**Edit → Version → Fabric Loader → Change version**) or
  import the zip again. The zip on the release is rebuilt with the new versions.
- **The server.** The Deck keeps its own mods folder (pbdr). packwiz could keep it in sync too
  (`java -jar packwiz-installer-bootstrap.jar -g -s server <pack.toml link>` before `start.sh`), but that isn't set up.
- **Configs.** The CurseForge import ships none either; Jaymo's Powers sets its presets on first launch.

## Other launchers

- **CurseForge app:** can't follow a packwiz pack. Keep using the Build Modpack zip there.
- **OneLauncher / OneClient (Polyfrost):** it has no packwiz support of its own, and it only updates packs published on
  Modrinth or CurseForge. It does have a per-profile pre-launch command, and that can run packwiz:
  `java -jar packwiz-installer-bootstrap.jar https://raw.githubusercontent.com/Kinslauer/jaymo-s-powerpack/tester-pack/pack.toml`
  with `packwiz-installer-bootstrap.jar` (from the packwiz-installer-bootstrap GitHub releases) in the profile's game
  folder. Unlike Prism it doesn't hand the command its own Java (`$INST_JAVA`), so `java` must be installed and on the
  PATH, and it drops that part of a Prism instance on import. Untested; Prism is the supported way.
